package GcodeSender.Generators;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class HilbertCurveGenerator implements GcodeGenerator {
	float turtle_x,turtle_y;
	float turtle_dx,turtle_dy;
	float turtle_step=10.0f;
	float xmax = 7;
	float xmin = -7;
	float ymax = 7;
	float ymin = -7;
	int order=4; // controls complexity of curve

	
	public String GetMenuName() {
		return "Hilbert Curve";
	}
	
	
	public void Generate() {
		// source http://introcs.cs.princeton.edu/java/32class/Hilbert.java.html

		try {
			String outputFile = System.getProperty("user.dir") + "/" + "TEMP.NGC";
			System.out.println("output file = "+outputFile);
			OutputStream output = new FileOutputStream(outputFile);
			output.write(new String("G28\n").getBytes());
			output.write(new String("G90\n").getBytes());
			output.write(new String("G54 X-30 Z-1.0\n").getBytes());
			
			turtle_x=0;
			turtle_y=0;
			turtle_dx=0;
			turtle_dy=-1;
			turtle_step = (float)((xmax-xmin) / (Math.pow(2, order)));

			// Draw bounding box
			output.write(new String("G90\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 Z0\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymin+"\n").getBytes());
			output.write(new String("G0 X"+xmin+" Y"+ymin+"\n").getBytes());
			output.write(new String("G0 X"+xmin+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 X"+xmax+" Y"+ymax+"\n").getBytes());
			output.write(new String("G0 Z0.5\n").getBytes());

			// move to starting position
			output.write(new String("G91\n").getBytes());
			output.write(new String("G0 X"+(-turtle_step/2)+" Y"+(-turtle_step/2)+"\n").getBytes());
			output.write(new String("G90\n").getBytes());
						
			// do the curve
			output.write(new String("G0 Z0\n").getBytes());
			output.write(new String("G91\n").getBytes());
			hilbert(output,order);
			output.write(new String("G90\n").getBytes());
			output.write(new String("G0 Z0.5\n").getBytes());

			// finish up
			output.write(new String("G28\n").getBytes());
			
        	output.flush();
	        output.close();
		}
		catch(IOException ex) {}
	}
	
	
    // Hilbert curve
    private void hilbert(OutputStream output,int n) throws IOException {
        if (n == 0) return;
        turtle_turn(90);
        treblih(output,n-1);
        turtle_goForward(output);
        turtle_turn(-90);
        hilbert(output,n-1);
        turtle_goForward(output);
        hilbert(output,n-1);
        turtle_turn(-90);
        turtle_goForward(output);
        treblih(output,n-1);
        turtle_turn(90);
    }


    // evruc trebliH
    public void treblih(OutputStream output,int n) throws IOException {
        if (n == 0) return;
        turtle_turn(-90);
        hilbert(output,n-1);
        turtle_goForward(output);
        turtle_turn(90);
        treblih(output,n-1);
        turtle_goForward(output);
        treblih(output,n-1);
        turtle_turn(90);
        turtle_goForward(output);
        hilbert(output,n-1);
        turtle_turn(-90);
    }
    

    public void turtle_turn(float degrees) {
    	double n = degrees * Math.PI / 180.0;
    	double newx =  Math.cos(n) * turtle_dx + Math.sin(n) * turtle_dy;
    	double newy = -Math.sin(n) * turtle_dx + Math.cos(n) * turtle_dy;
    	double len = Math.sqrt(newx*newx + newy*newy);
    	assert(len>0);
    	turtle_dx = (float)(newx/len);
    	turtle_dy = (float)(newy/len);
    }

    
    public void turtle_goForward(OutputStream output) throws IOException {
    	//turtle_x += turtle_dx * distance;
    	//turtle_y += turtle_dy * distance;
    	//output.write(new String("G0 X"+(turtle_x)+" Y"+(turtle_y)+"\n").getBytes());
    	output.write(new String("G0 X"+(turtle_dx*turtle_step)+" Y"+(turtle_dy*turtle_step)+"\n").getBytes());
    }
}
