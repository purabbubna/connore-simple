package com.connore;

import com.connore.util.Config;
import com.connore.util.Db;
import com.connore.web.HttpApplication;
import java.awt.Desktop;
import java.net.URI;
import java.util.Arrays;

/** Starts the standalone Java HTTP application. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        var config = Config.load();
        Db.configure(config);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Db.query("SELECT account_id FROM account LIMIT 1");
        } catch (Exception exception) {
            System.err.println("Cannot connect to Connore. Check MySQL, the existing schema, Connector/J in lib, and your .env settings.");
            Db.close();
            System.exit(1);
            return;
        }

        var application = new HttpApplication(config);
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "connore-shutdown"));
        application.start();
        String localUrl = "http://localhost:" + application.port();
        System.out.println("Connore Simple is running at " + localUrl);
        System.out.println("Press Ctrl+C to stop.");
        if (Arrays.asList(args).contains("--open-browser")) openBrowser(localUrl);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) { }
        System.out.println("Open " + url + " in your browser.");
    }
}
