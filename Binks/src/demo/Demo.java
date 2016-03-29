package demo;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

public class Demo {

	public static void main(String[] args) {
		System.out.println("hej");
		Shell shell = new Shell();
		MessageBox dialog = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
		dialog.setText("This");
		dialog.setMessage("is a demo");

		// open dialog and await user selection
		int returnCode = dialog.open();
		System.out.println((returnCode == SWT.OK)? "OK": "Cancel" );

	}
}
