package com.marginallyclever.gcodesender;

import java.awt.Dimension;

import javax.swing.JLabel;

public class StatusBar extends JLabel {
	private static final long serialVersionUID=1;

    /** Creates a new instance of StatusBar */
    public StatusBar() {
        super();
        super.setPreferredSize(new Dimension(100, 16));
        setMessage("Ready");
    }

    public void setMessage(String message) {
        setText(" "+message);        
    }        
}