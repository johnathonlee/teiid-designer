/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

/**
 * 
 */
package org.teiid.transport;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

import com.metamatrix.common.comm.platform.CommPlatformPlugin;
import com.metamatrix.common.comm.platform.socket.ObjectChannel;
import com.metamatrix.common.log.LogManager;
import com.metamatrix.common.util.LogConstants;

/**
 * Main class for creating Netty Nio Channels 
 */

@ChannelPipelineCoverage(ChannelPipelineCoverage.ALL)
public class SSLAwareChannelHandler extends SimpleChannelHandler implements ChannelPipelineFactory {
	
	public class ObjectChannelImpl implements ObjectChannel {
		private final Channel channel;

		public ObjectChannelImpl(Channel channel) {
			this.channel = channel;
		}

		public void close() {
			channel.close();
		}

		public boolean isOpen() {
			return channel.isOpen();
		}
		
		public SocketAddress getRemoteAddress() {
			return channel.getRemoteAddress();
		}
		
		@Override
		public Object read() throws IOException,
				ClassNotFoundException {
			throw new UnsupportedOperationException();
		}

		public Future<?> write(Object msg) {
			final ChannelFuture future = channel.write(msg);
			future.addListener(completionListener);
			return new Future<Void>() {

				@Override
				public boolean cancel(boolean arg0) {
					return future.cancel();
				}

				@Override
				public Void get() throws InterruptedException,
						ExecutionException {
					future.await();
					if (!future.isSuccess()) {
						throw new ExecutionException(future.getCause());
					}
					return null;
				}

				@Override
				public Void get(long arg0, TimeUnit arg1)
						throws InterruptedException, ExecutionException,
						TimeoutException {
					if (future.await(arg0, arg1)) {
						if (!future.isSuccess()) {
							throw new ExecutionException(future.getCause());
						}
						return null;
					}
					throw new TimeoutException();
				}

				@Override
				public boolean isCancelled() {
					return future.isCancelled();
				}

				@Override
				public boolean isDone() {
					return future.isDone();
				}
			};
		}
	}
	
	private final ChannelListener.ChannelListenerFactory listenerFactory;
	private final SSLEngine engine;
	private final ClassLoader classLoader;
	private Map<Channel, ChannelListener> listeners = Collections.synchronizedMap(new HashMap<Channel, ChannelListener>());
	private AtomicLong objectsRead = new AtomicLong(0);
	private AtomicLong objectsWritten = new AtomicLong(0);
	private volatile int maxChannels;
	
	private ChannelFutureListener completionListener = new ChannelFutureListener() {

		@Override
		public void operationComplete(ChannelFuture arg0)
				throws Exception {
			if (arg0.isSuccess()) {
				objectsWritten.getAndIncrement();
			}
		}
		
	};
	 
	public SSLAwareChannelHandler(ChannelListener.ChannelListenerFactory listenerFactory,
			SSLEngine engine, ClassLoader classloader) {
		this.listenerFactory = listenerFactory;
		this.engine = engine;
		this.classLoader = classloader;
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx,
			final ChannelStateEvent e) throws Exception {
		ChannelListener listener = this.listenerFactory.createChannelListener(new ObjectChannelImpl(e.getChannel()));
		synchronized (this.listeners) {
			this.listeners.put(e.getChannel(), listener);
			maxChannels = Math.max(maxChannels, this.listeners.size());
		}
		if (engine != null) {
			SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
	        sslHandler.handshake(e.getChannel()).addListener(new ChannelFutureListener() {
	        	public void operationComplete(ChannelFuture arg0)
	        			throws Exception {
	        		onConnection(e.getChannel());
	        	}
	        });
		} else {
			onConnection(e.getChannel());
		}
	}
	
	private void onConnection(Channel channel) throws Exception {
		ChannelListener listener = this.listeners.get(channel);
		if (listener != null) {
			listener.onConnection();
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx,
			ExceptionEvent e) throws Exception {
		ChannelListener listener = this.listeners.get(e.getChannel());
		if (listener != null) {
			listener.exceptionOccurred(e.getCause());
		}
		e.getChannel().close();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx,
			MessageEvent e) throws Exception {
		objectsRead.getAndIncrement();
		ChannelListener listener = this.listeners.get(e.getChannel());
		if (listener != null) {
			listener.receivedMessage(e.getMessage());
		}
	}
	
	@Override
	public void channelDisconnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		ChannelListener listener = this.listeners.remove(e.getChannel());
		if (listener != null) {
			LogManager.logDetail(LogConstants.CTX_SERVER, CommPlatformPlugin.Util.getString("SSLAwareChannelHandler.channel_closed")); //$NON-NLS-1$
		}
	}

	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = new DefaultChannelPipeline();

	    if (engine != null) {
	        pipeline.addLast("ssl", new SslHandler(engine)); //$NON-NLS-1$
	    }
	    pipeline.addLast("decoder", new ObjectDecoder(1 << 24, classLoader)); //$NON-NLS-1$
	    pipeline.addLast("encoder", new ObjectEncoder()); //$NON-NLS-1$
	    pipeline.addLast("handler", this); //$NON-NLS-1$
	    return pipeline;
	}
	
	public long getObjectsRead() {
		return this.objectsRead.get();
	}
	
	public long getObjectsWritten() {
		return this.objectsWritten.get();
	}
	
	public int getConnectedChannels() {
		return this.listeners.size();
	}
	
	public int getMaxConnectedChannels() {
		return this.maxChannels;
	}

}