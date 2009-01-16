package net.sourceforge.sqlexplorer.dbviewer.details;

/*
 * Copyright (C) 2002-2004 Andrea Mazzolini
 * andreamazzolini@users.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
 
class PKDetailSubRow implements PKInterface{
	PKDetailSubRow(PKDetailRow idr,String name,short order){
		parent=idr;
		el=new Object[2];
		el[0]=name;		
		el[1]=new Integer(order);
	}
	java.util.ArrayList ls=new java.util.ArrayList();
	PKDetailRow parent ;
	Object []el;
	public Object getValue(int k){
		return el[k];
	}
	public Object getParent(){
		return parent;
	}	
	public Object[] getChildren(){
		return ls.toArray();
	}
}
