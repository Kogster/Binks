package binks;

import java.awt.GraphicsEnvironment;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class LoadingScreen extends JFrame {
	public LoadingScreen() {
		super();
		this.setUndecorated(true);
		GraphicsEnvironment ge = GraphicsEnvironment
				.getLocalGraphicsEnvironment();
		this.setLocation(ge.getCenterPoint());
		ImageIcon icon;
		try {
			icon = new ImageIcon(ImageIO.read(this.getClass().getClassLoader()
					.getResource("PlaceholderLaunchScreen.png")));
			add(new JLabel(icon));
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	public void disposeIt() {
		new Thread(new Runnable(){

			@Override
			public void run() {
				try {
					Thread.sleep(1000); //Give the program time to launch in the background 
				} catch (InterruptedException e) {
				}
				setVisible(false);
				dispose();
			}
		}).start();
	}
}
