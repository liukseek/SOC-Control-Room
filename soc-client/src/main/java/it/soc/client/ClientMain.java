package it.soc.client;

public class ClientMain {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        ConnectionWindow.main(args);
    }
}
