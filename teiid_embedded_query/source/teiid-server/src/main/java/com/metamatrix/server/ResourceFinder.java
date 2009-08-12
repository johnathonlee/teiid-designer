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

package com.metamatrix.server;


import org.teiid.dqp.internal.cache.DQPContextCache;

import com.google.inject.Injector;
import com.metamatrix.common.messaging.MessageBus;
import com.metamatrix.common.messaging.NoOpMessageBus;
import com.metamatrix.platform.config.spi.xml.XMLConfigurationMgr;

public class ResourceFinder extends com.metamatrix.dqp.ResourceFinder {
	
	public static MessageBus getMessageBus() {
		if (injector == null) {
			return new NoOpMessageBus();
		}
		return injector.getInstance(MessageBus.class);
	}	
	
	public static void setInjectorAndCompleteInitialization(Injector injector) {
		ResourceFinder.setInjector(injector);
		XMLConfigurationMgr.getInstance().setMessageBus(getMessageBus());
	}

	public static DQPContextCache getContextCache() {
		return injector.getInstance(DQPContextCache.class);
	}
}
