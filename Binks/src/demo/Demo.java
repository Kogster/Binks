package demo;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

public class Demo {

	public static void main(String[] args) {
		System.out.println("hej");
		Shell shell = new Shell();
		MessageBox dialog = 
		new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK| SWT.CANCEL);
		dialog.setText("My info");
		dialog.setMessage("Do you really want to do this?");

		// open dialog and await user selection
		int returnCode = dialog.open();
		System.out.println(returnCode);

	}
}
