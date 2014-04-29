package GcodeSender.Generators;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import GcodeSender.GcodeSender;

public class YourMessageHereGenerator implements GcodeGenerator {
	protected float kerning=-0.50f;
	protected float letter_width=2.0f;
	protected float letter_height=2.0f;
	protected float line_spacing=0.5f;
	protected float margin=1.0f;
	static final String alphabetFolder = new String("ALPHABET/");
	protected int chars_per_line=35;
	String lastMessage = "";
	
	
	public String GetMenuName() {
		return new String("Your message here");
	}
	
	
	public void Generate() {
		final JDialog driver = new JDialog(GcodeSender.getSingleton().GetMainFrame(),"Your Message Here",true);
		driver.setLayout(new GridLayout(0,1));

		final JTextArea text = new JTextArea(lastMessage,40,4);
		final JButton buttonSave = new JButton("Go");
		final JButton buttonCancel = new JButton("Cancel");

		driver.add(new JScrollPane(text));
		
		Box horizontalBox = Box.createHorizontalBox();
	    horizontalBox.add(Box.createGlue());
	    horizontalBox.add(buttonSave);
	    horizontalBox.add(buttonCancel);
	    driver.add(horizontalBox);
		
		ActionListener driveButtons = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object subject = e.getSource();
				
				if(subject == buttonSave) {
					lastMessage = text.getText();
					CreateMessageNow(lastMessage);
					
					driver.dispose();
				}
				if(subject == buttonCancel) {
					driver.dispose();
				}
			}
		};
		
		buttonSave.addActionListener(driveButtons);
		buttonCancel.addActionListener(driveButtons);

		driver.setSize(300,100);
		driver.setVisible(true);
	}

	
	protected void CreateMessageNow(String text) {
		String outputFile = System.getProperty("user.dir") + "/" + "TEMP.NGC";
		System.out.println("output file = "+outputFile);
		
		try {
			OutputStream output = new FileOutputStream(outputFile);
			output.write(new String("G28\n").getBytes());
			output.write(new String("G90\n").getBytes());
			output.write(new String("G54 Y-25 Z-1.0\n").getBytes());
			output.write(new String("G0 F600\n").getBytes());

			// find size of text block
			int num_lines = (int)Math.ceil( (float)text.length() / chars_per_line );
			int len = chars_per_line;
			
			float char_width = letter_width + kerning;
			
			float total_height = letter_height * num_lines + line_spacing * (num_lines-1);
			
			float xmax = len/2.0f * char_width + margin;  // center the text, go left 50%
			float xmin = -xmax;
			float ymax = total_height/2 + margin;
			float ymin = -ymax;
			
			
			System.out.println("x "+xmin+" to "+xmax);
			System.out.println("y "+ymin+" to "+ymax);
			
			// draw bounding box
			output.write(new String("G90\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 Z0\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymin+"\n").getBytes());
			output.write(new String("G0 X"+xmin+" Y"+ymin+"\n").getBytes());
			output.write(new String("G0 X"+xmin+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 Z0.5\n").getBytes());
			output.write(new String("G0 X0 Y0\n").getBytes());


			// move to first line height
			float baseline = ymax - margin - letter_height;

			float message_start = -chars_per_line * 0.5f * char_width;
			output.write(new String("G91\n").getBytes());
			output.write(new String("G0 X"+message_start+" Y"+baseline+"\n").getBytes());

			float line_start = -chars_per_line * char_width;
			float next_line = -(letter_height + line_spacing);
			
			int i;
			for(i=0; i<text.length(); i+=chars_per_line) {
				// draw line of text
				int end = i+chars_per_line;
				if(end>text.length()) end = text.length();
				
				String subtext = text.substring(i,end);
				DrawMessageLine(subtext,output);
				output.write(new String("\n").getBytes());
				
				output.write(new String("G91\n").getBytes());
				output.write(new String("G0 X"+line_start+" Y"+next_line+"\n").getBytes());
			}

			output.write(new String("G90\n").getBytes());
			output.write(new String("G0 Z5 F1000\n").getBytes());
//			output.write(new String("G28\n").getBytes());
			
        	output.flush();
	        output.close();
		}
		catch(IOException ex) {}
	}

	protected void DrawMessageLine(String a1,OutputStream output) throws IOException {
		String wd = System.getProperty("user.dir") + "/";
		String ud = wd + alphabetFolder;
		
		System.out.println(a1);
		System.out.println(a1.length());
		int i=0;
		for(i=0;i<a1.length();++i) {
			char c = a1.charAt(i);
			String name;
			// find the file that goes with this character
			// TODO load these from an XML description
			if('a'<= c && c <= 'z') {
				name="SMALL_" + Character.toUpperCase(c);
			} else {
				switch(c) {
				case ' ':  name="SPACE";  break;
				case '!':  name="EXCLAMATION";  break;
				case '"':  name="DOUBLEQ";  break;
				case '$':  name="DOLLAR";  break;
				case '#':  name="POUND";  break;
				case '%':  name="PERCENT";  break;
				case '&':  name="AMPERSAND";  break;				
				case '\'':  name="SINGLEQ";  break;
				case '(':  name="B1OPEN";  break;
				case ')':  name="B1CLOSE";  break;
				case '*':  name="ASTERIX";  break;
				case '+':  name="PLUS";  break;
				case ',':  name="COMMA";  break;
				case '-':  name="HYPHEN";  break;
				case '.':  name="PERIOD";  break;
				case '/':  name="FSLASH";  break;
				case ':':  name="COLON";  break;
				case ';':  name="SEMICOLON";  break;
				case '<':  name="GREATERTHAN";  break;
				case '=':  name="EQUAL";  break;
				case '>':  name="LESSTHAN";  break;
				case '?':  name="QUESTION";  break;
				case '@':  name="AT";  break;
				case '[':  name="B2OPEN";  break;
				case ']':  name="B2CLOSE";  break;
				case '^':  name="CARET";  break;
				case '_':  name="UNDERSCORE";  break;
				case '`':  name="GRAVE";  break;
				case '{':  name="B3OPEN";  break;
				case '|':  name="BAR";  break;
				case '}':  name="B3CLOSE";  break;
				case '~':  name="TILDE";  break;
				case '\\':  name="BSLASH";  break;
				case 'É':  name="SPACE";  break;
				default: name=Character.toString(c);  break;
				}
			}
			String fn = ud + name  + ".NGC";
			//System.out.print(fn);
			
			
			if(new File(fn).isFile()) {
				// file found. copy/paste it into the temp file
				//System.out.println(" OK");
				InputStream in = new FileInputStream(fn);
				byte[] buf = new byte[1000];
		        int b = 0;
		        while ( (b = in.read(buf)) >= 0) {
		        	output.write(buf, 0, b);
		        }
				output.write(new String("\n").getBytes());
				if(kerning!=0) {
					output.write(new String("G0 X"+kerning+"\n").getBytes());
				}
			} else {
				// file not found
				System.out.print(fn);
				System.out.println(" NOK");
			}
		}
	}
}
