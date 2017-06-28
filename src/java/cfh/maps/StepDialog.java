package cfh.maps;

import static java.awt.GridBagConstraints.*;

import java.awt.Component;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;


public class StepDialog {
    
    private JDialog dialog;
    
    private JLabel label;
    private JTextField message;
    private JButton stepButton;
    private JButton runButton;
    
    private boolean stepping = false;
    private Lock lock;
    private Condition step;
    
    
    public StepDialog(Component parent, String title) {
        label = new JLabel(title);
        
        message = new JTextField(20);
        message.setEditable(false);
        
        stepButton = new JButton("Step");
        stepButton.addActionListener(this::doStep);
        
        runButton = new JButton("Run");
        runButton.addActionListener(this::doRun);
        
        Component root = SwingUtilities.getRoot(parent);
        dialog = root instanceof Frame ? new JDialog((Frame) root) : new JDialog((Window) root);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new GridBagLayout());
        Insets insets = new Insets(0, 0, 0, 0);
        dialog.add(label, new GridBagConstraints(0, 0, REMAINDER, 1, 0.0, 0.0, CENTER, NONE, insets, 0, 0));
        dialog.add(message, new GridBagConstraints(0, RELATIVE, REMAINDER, 1, 0.0, 0.0, LINE_START, HORIZONTAL, insets, 0, 0));
        dialog.add(stepButton, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, CENTER, NONE, insets, 0, 0));
        dialog.add(runButton, new GridBagConstraints(RELATIVE, RELATIVE, REMAINDER, 1, 1.0, 0.0, CENTER, NONE, insets, 0, 0));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        
        lock = new ReentrantLock();
        step = lock.newCondition();
    }
    
    public void show() {
        stepping = true;
        dialog.setVisible(true);
        stepButton.setEnabled(false);
        runButton.setEnabled(false);
    }
    
    public void dispose() {
        stepping = false;
        dialog.dispose();
    }
    
    public void doStep(ActionEvent ev) {
        stepButton.setEnabled(false);
        runButton.setEnabled(false);
        if (stepping) {
            lock.lock();
            try {
                step.signal();
            } finally {
                lock.unlock();
            }
        }
    }
    
    public void doRun(ActionEvent ev) {
        stepButton.setEnabled(false);
        runButton.setEnabled(false);
        if (stepping) {
            stepping = false;
            lock.lock();
            try {
                step.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    public void waitStep(String format, Object... args) {
        message.setText(String.format(format, args));
        if (stepping) {
            stepButton.setEnabled(true);
            runButton.setEnabled(true);
            lock.lock();
            try {
                step.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

}
