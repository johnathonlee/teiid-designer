package net.sourceforge.sqlexplorer.sqlpanel;

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
import net.sourceforge.sqlexplorer.Messages;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

public class SqlTableLabelProvider
	extends LabelProvider
	implements ITableLabelProvider {
	
	SqlTableModel md;

	public SqlTableLabelProvider(SqlTableModel md){
		this.md=md;
	}
	/**
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(Object, int)
	 */
	public final Image getColumnImage(Object arg0, int arg1) {
		return null;
	}
	static final String nullString=Messages.getString("<NULL>_1"); //$NON-NLS-1$
	/**
	 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(Object, int)
	 */
	public final String getColumnText(Object element, int columnIndex) {
		Object obj=((SqlRowElement)element).getValue(columnIndex);
		if(obj!=null)
			return obj.toString();
        return nullString;
	}

}

