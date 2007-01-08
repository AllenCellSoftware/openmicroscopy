/*
 * org.openmicroscopy.shoola.util.ui.lens.LensMenu 
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package org.openmicroscopy.shoola.util.ui.lens;

//Java imports
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;


//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.util.ui.ColorMenuItem;

/** 
 * LensMenu is a singleton class which creates both the popupmenus and menubars
 * used in the lensUI and zoomWindowUI.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author	Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
class LensMenu 
{
	
	/** Text for the popup Menu -- not shown. */
	final static String POPUP_MENU_DESCRIPTION = "Magnifying Lens Options"; 
	
	/** Text for the popup menu -- shown as a top option to the user. */
	final static String POPUP_MENU_TOPOPTION = "Magnifying Lens Options"; 
	
	/** 
	 * Text for the lens options -- parent of the resizing methods for the 
	 * lens. 
	 * */
	final static String LENS_OPTIONS = "Lens";
	
	/** 
	 * Text for the zoom options -- parent of the changing of magnification 
	 * methods for the lens. 
	 */
	final static String ZOOM_OPTIONS = "Zoom";
	
	/** 
	 * Text for the option to display units -- parent of the micron/pixel 
	 * options. 
	 */
	final static String DISPLAY_UNITS = "Units";
	
	/** 
	 * Text for the option to change the colour of the lens.
	 */
	final static String LENS_COLOR_OPTIONS = "Lens Color";
	
	/** Parent component of the lens and zoomWindowUI. */
	private LensComponent		lensComponent;

	/** The menubar which holds the menu items. */
	private JPopupMenu			popupMenu;
	
	/** The menubar which holds the menu items. */
	private JMenuBar			menubar;

	/**
	 * Create the menu and attach the lens component. 
	 * 
	 * @param lensComponent
	 */
	LensMenu(LensComponent lensComponent)
	{
		this.lensComponent = lensComponent;
		createPopupMenu();
		createMenubarMenu();
	}
	
	/**
	 * Gets the popup version of the menu. 
	 * 
	 * @return See above.
	 */
	JPopupMenu getPopupMenu() { return popupMenu; }
	
	/**
	 * Gets the menubar version of the menu. 
	 * 
	 * @return See above.
	 */
	JMenuBar getMenubar() { return menubar; }
	
	/**
	 * Create the menu which will allow the user to adjust the size of the lens.
	 * 
	 * @return The lens sizing menu.
	 */
	private JMenu createLensOptions()
	{
		JMenu lensOptions = new JMenu(LENS_OPTIONS);
		JMenuItem setLensSize;
		
		for (int indexCnt = 0 ; indexCnt < LensAction.MAX ; indexCnt++)
		{
			setLensSize = new JMenuItem(new LensAction(lensComponent, 
																	indexCnt));
			lensOptions.add(setLensSize);
		}
		return lensOptions;
	}
	
	/**
	 * Create the menu which will allow the user to adjust the zooming of 
	 * the lens.
	 * 
	 * @return The lens zooming menu.
	 */
	private JMenu createZoomOptions()
	{
		JMenu					zoomOptions;
		JMenuItem				setLensZoom;
		zoomOptions = new JMenu(ZOOM_OPTIONS);
		for (int indexCnt = 0 ; indexCnt < ZoomAction.MAX ; indexCnt++)
		{
			setLensZoom = new JMenuItem(new ZoomAction(lensComponent, 
																	indexCnt));
			zoomOptions.add(setLensZoom);
		}
		return zoomOptions;
	}
	
	/**
	 * Create the menu which allows the user to adjust the colour of the lens.
	 * 
	 * @return The menu which allows the chaning of the lens colour.
	 */
	private JMenu createLensColorOptions()
	{
		JMenu					lensColorOptions;
		ColorMenuItem			lensColor;
		LensColorAction lensColorAction;
		lensColorOptions = new JMenu(LENS_COLOR_OPTIONS);
		for (int indexCnt = 0; indexCnt < LensColorAction.MAX ; indexCnt++)
		{
			lensColorAction = new LensColorAction(lensComponent, indexCnt);
			lensColor = new ColorMenuItem(lensColorAction.getColor());
			lensColor.addActionListener(lensColorAction);
			lensColor.setText(lensColorAction.getName());
			lensColorOptions.add(lensColor);
		}
		return lensColorOptions;
	}
	
	/**
	 * Create the menu which will allow the user to change the units the lens 
	 * will be measured and positioned in. (Micron or pixels); 
	 * 
	 * @return The lens select units menu.
	 */
	private JMenu createDisplayOptions()
	{
		JMenu					displayOptions;
		JCheckBoxMenuItem	setDisplayScale;
		displayOptions = new JMenu(DISPLAY_UNITS);
		ButtonGroup displayUnits = new ButtonGroup();
		for (int indexCnt = 0 ; indexCnt < DisplayAction.MAX ; indexCnt++)
		{
			setDisplayScale = new JCheckBoxMenuItem(new DisplayAction
													(lensComponent, indexCnt));
			displayUnits.add(setDisplayScale);
			displayOptions.add(setDisplayScale);
			setDisplayScale.setSelected(indexCnt == 1);
		}
		return displayOptions;
	}
	
	/** 
	 * Creates the popmenu for the lens, allow the user to change settings:
	 * zoom factor, lens size and display units.
	 */
	private void createPopupMenu()
	{
		JMenuItem 				topOption;
			
		popupMenu = new JPopupMenu(POPUP_MENU_DESCRIPTION);
		topOption = new JMenuItem(POPUP_MENU_TOPOPTION);
		popupMenu.add(topOption);
		popupMenu.addSeparator();
		
		popupMenu.add(createLensOptions());
		popupMenu.add(createZoomOptions());
		popupMenu.add(createDisplayOptions());
		popupMenu.add(createLensColorOptions());
	}
	
	/** 
	 * Creates the popmenu for the lens, allow the user to change settings:
	 * zoom factor, lens size and display units.
	 */
	private void createMenubarMenu()
	{
		menubar = new JMenuBar();
		
		menubar.add(createLensOptions());
		menubar.add(createZoomOptions());
		menubar.add(createDisplayOptions());
		menubar.add(createLensColorOptions());
	}

}


