import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class Launcher {

    private static File baseDir;

    public static void main(String[] args) throws Exception {
        baseDir = findBaseDir();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(Launcher::showWindow);
    }

    private static File findBaseDir() throws URISyntaxException {
        File self = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return self.getParentFile();
    }

    private static void showWindow() {
        JFrame frame = new JFrame("SOC: CONTROL ROOM");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel("SOC: CONTROL ROOM", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title);
        panel.add(button("Gioca (client)", () -> start("client")));
        panel.add(button("Avvia server", () -> start("server")));
        panel.add(button("Test locale (server + 3 client)", () -> {
            start("server");
            sleep(2500);
            start("client");
            start("client");
            start("client");
        }));
        frame.setContentPane(panel);
        frame.pack();
        frame.setSize(340, frame.getHeight());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static void start(String module) {
        File jar = findJar(module);
        if (jar == null) {
            JOptionPane.showMessageDialog(null, "Jar del " + module + " non trovato. Esegui prima compila.bat.",
                    "SOC: CONTROL ROOM", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        try {
            new ProcessBuilder(java, "-jar", jar.getAbsolutePath())
                    .directory(jar.getParentFile())
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Avvio non riuscito: " + e.getMessage(),
                    "SOC: CONTROL ROOM", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static File findJar(String module) {
        File[] candidates = {
                new File(baseDir, "soc-" + module + ".jar"),
                new File(baseDir, "soc-" + module + File.separator + "target" + File.separator + "soc-" + module + "-1.0.0-SNAPSHOT.jar")
        };
        for (File f : candidates) {
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
