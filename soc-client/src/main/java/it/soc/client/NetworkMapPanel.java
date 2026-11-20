package it.soc.client;

import it.soc.common.model.Role;
import it.soc.common.model.SystemNode;
import it.soc.common.model.SystemStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class NetworkMapPanel extends JComponent {

    private static final Map<String, double[]> POSITIONS = Map.of(
            "INET", new double[]{0.5, 0.05},
            "FW", new double[]{0.5, 0.36},
            "VPN", new double[]{0.15, 0.3},
            "MAIL", new double[]{0.85, 0.3},
            "CORE", new double[]{0.5, 0.58},
            "FILE", new double[]{0.17, 0.82},
            "DB", new double[]{0.5, 0.86},
            "HR", new double[]{0.83, 0.82}
    );

    private static final String[][] LINKS = {
            {"INET", "FW"}, {"INET", "VPN"}, {"FW", "MAIL"}, {"FW", "CORE"},
            {"VPN", "CORE"}, {"MAIL", "CORE"}, {"CORE", "FILE"}, {"CORE", "DB"},
            {"CORE", "HR"}
    };

    private static final Map<String, String[]> EXIT_PATHS = Map.of(
            "FW", new String[]{"FW", "INET"},
            "VPN", new String[]{"VPN", "INET"},
            "MAIL", new String[]{"MAIL", "FW", "INET"},
            "FILE", new String[]{"FILE", "CORE", "FW", "INET"},
            "DB", new String[]{"DB", "CORE", "FW", "INET"},
            "HR", new String[]{"HR", "CORE", "FW", "INET"}
    );

    private final Map<String, SystemNode> nodes = new LinkedHashMap<>();
    private final Map<String, Double> hover = new HashMap<>();
    private final Map<String, Double> flash = new HashMap<>();
    private final List<Packet> packets = new ArrayList<>();
    private final Random random = new Random();
    private final Role role;

    private Consumer<String> selectionListener;
    private String selected;
    private String hovered;
    private double phase;
    private long lastSpawn;
    private long lastAlertSpawn;
    private boolean interactive = true;

    public NetworkMapPanel(Role role) {
        this.role = role;
        setOpaque(false);
        setPreferredSize(new Dimension(640, 420));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                String id = nodeAt(e.getPoint());

                if (id == null ? hovered != null : !id.equals(hovered)) {
                    hovered = id;
                    setCursor(Cursor.getPredefinedCursor(
                            id != null && interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
                    ));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                String id = nodeAt(e.getPoint());

                if (id != null && interactive) {
                    select(id);
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        new Timer(30, e -> animate()).start();
    }

    public void setSelectionListener(Consumer<String> listener) {
        this.selectionListener = listener;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void select(String id) {
        selected = id;
        flash.put(id, 1.0);

        if (selectionListener != null) {
            selectionListener.accept(id);
        }
    }

    public String selected() {
        return selected;
    }

    public void update(List<SystemNode> list) {
        for (SystemNode node : list) {
            SystemNode previous = nodes.get(node.id());

            if (previous != null && previous.status() != node.status()) {
                flash.put(node.id(), 1.0);
            }

            nodes.put(node.id(), node);
        }
    }

    public void pulse(String id) {
        if (id != null) {
            flash.put(id, 1.0);
        }
    }

    public SystemNode node(String id) {
        return nodes.get(id);
    }

    private void animate() {
        phase += 0.03;

        for (String id : POSITIONS.keySet()) {
            double target = id.equals(hovered) && isSystem(id) && interactive ? 1 : 0;
            hover.put(id, Anim.approach(hover.getOrDefault(id, 0.0), target, 0.2));
        }

        flash.replaceAll((id, value) -> Math.max(0, value - 0.025));

        long now = System.currentTimeMillis();

        if (now - lastSpawn > 220) {
            lastSpawn = now;
            String[] link = LINKS[random.nextInt(LINKS.length)];
            boolean reverse = random.nextBoolean();

            if (!isolated(link[0]) && !isolated(link[1])) {
                packets.add(new Packet(
                        reverse ? new String[]{link[1], link[0]} : link,
                        0.012 + random.nextDouble() * 0.01,
                        Theme.alpha(role == Role.INSIDER ? new Color(200, 140, 130) : new Color(150, 195, 215), 200),
                        2.2
                ));
            }
        }

        if (now - lastAlertSpawn > 260) {
            lastAlertSpawn = now;

            for (SystemNode node : nodes.values()) {
                if (node.alert() && EXIT_PATHS.containsKey(node.id())) {
                    packets.add(new Packet(EXIT_PATHS.get(node.id()), 0.02, Theme.DANGER, 3.4));
                }
            }
        }

        Iterator<Packet> iterator = packets.iterator();

        while (iterator.hasNext()) {
            Packet packet = iterator.next();
            packet.progress += packet.speed;

            if (packet.progress >= packet.path.length - 1) {
                iterator.remove();
            }
        }

        repaint();
    }

    private boolean isolated(String id) {
        SystemNode node = nodes.get(id);
        return node != null && node.status() == SystemStatus.ISOLATED;
    }

    private boolean isSystem(String id) {
        return !"INET".equals(id) && !"CORE".equals(id);
    }

    private double scale() {
        return Math.max(0.7, Math.min(1.25, Math.min(getWidth() / 640.0, getHeight() / 420.0)));
    }

    private Point2D position(String id) {
        double[] p = POSITIONS.get(id);
        double padX = 60 * scale();
        double padY = 40 * scale();

        return new Point2D.Double(
                padX + p[0] * (getWidth() - padX * 2),
                padY + p[1] * (getHeight() - padY * 2)
        );
    }

    private String nodeAt(Point point) {
        double radius = 38 * scale();

        for (String id : nodes.keySet()) {
            if (POSITIONS.containsKey(id) && position(id).distance(point) < radius) {
                return id;
            }
        }

        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        drawBackdrop(g2);
        drawLinks(g2);
        drawPackets(g2);

        drawInfrastructure(g2, "INET", "INTERNET");
        drawInfrastructure(g2, "CORE", "CORE SWITCH");

        for (SystemNode node : nodes.values()) {
            if (POSITIONS.containsKey(node.id())) {
                drawNode(g2, node);
            }
        }

        g2.setFont(Theme.font(10, Font.PLAIN));
        g2.setColor(Theme.alpha(Theme.MUTED, 150));
        g2.drawString(interactive ? "Clicca un sistema per selezionarlo" : "Mappa in sola lettura",
                12, getHeight() - 10);

        g2.dispose();
    }

    private void drawBackdrop(Graphics2D g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(new Color(130, 160, 180, 14));

        for (int x = 12; x < w; x += 24) {
            for (int y = 12; y < h; y += 24) {
                g.fillRect(x, y, 1, 1);
            }
        }

        Point2D center = position("CORE");
        double radius = Math.max(w, h) * 0.75;
        double angle = Math.toDegrees(phase * 0.6) % 360;
        Color sweep = role == Role.INSIDER ? new Color(214, 96, 86) : new Color(112, 160, 182);

        for (int i = 0; i < 28; i++) {
            g.setColor(Theme.alpha(sweep, 16 - i / 2));
            g.fill(new Arc2D.Double(center.getX() - radius, center.getY() - radius,
                    radius * 2, radius * 2, -angle + i * 1.6, 1.8, Arc2D.PIE));
        }

        g.setStroke(new BasicStroke(1f));

        for (int i = 1; i <= 3; i++) {
            double r = 90 * scale() * i;
            g.setColor(new Color(130, 160, 180, 14));
            g.draw(new Ellipse2D.Double(center.getX() - r, center.getY() - r, r * 2, r * 2));
        }
    }

    private void drawLinks(Graphics2D g) {
        for (String[] link : LINKS) {
            Point2D a = position(link[0]);
            Point2D b = position(link[1]);

            boolean cut = isolated(link[0]) || isolated(link[1]);

            if (cut) {
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{4f, 6f}, (float) ((phase * 8) % 10)));
                g.setColor(new Color(150, 120, 110, 80));
            } else {
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(135, 170, 190, 40));
                g.draw(new Line2D.Double(a, b));
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(150, 185, 205, 70));
            }

            g.draw(new Line2D.Double(a, b));
        }
    }

    private void drawPackets(Graphics2D g) {
        for (Packet packet : packets) {
            int index = (int) Math.floor(packet.progress);
            double local = packet.progress - index;

            if (index + 1 >= packet.path.length) {
                continue;
            }

            Point2D a = position(packet.path[index]);
            Point2D b = position(packet.path[index + 1]);

            double x = a.getX() + (b.getX() - a.getX()) * local;
            double y = a.getY() + (b.getY() - a.getY()) * local;
            double s = packet.size * scale();

            g.setColor(Theme.alpha(packet.color, 50));
            g.fill(new Ellipse2D.Double(x - s * 2.6, y - s * 2.6, s * 5.2, s * 5.2));
            g.setColor(packet.color);
            g.fill(new Ellipse2D.Double(x - s, y - s, s * 2, s * 2));
        }
    }

    private void drawInfrastructure(Graphics2D g, String id, String label) {
        Point2D p = position(id);
        double s = scale();
        double r = 20 * s;

        g.setPaint(new RadialGradientPaint(p, (float) (r * 2.4f),
                new float[]{0f, 1f},
                new Color[]{new Color(112, 142, 162, 40), new Color(112, 142, 162, 0)}));
        g.fill(new Ellipse2D.Double(p.getX() - r * 2.4, p.getY() - r * 2.4, r * 4.8, r * 4.8));

        g.setColor(Theme.PANEL_DARK);
        g.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));
        g.setColor(new Color(112, 142, 162, 150));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));

        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(p.getX(), p.getY());
        icon.scale(s, s);
        icon.setColor(Theme.STEEL_HOVER);
        icon.setStroke(new BasicStroke(1.4f));

        if ("INET".equals(id)) {
            icon.draw(new Ellipse2D.Double(-9, -9, 18, 18));
            icon.draw(new Ellipse2D.Double(-4.5, -9, 9, 18));
            icon.draw(new Line2D.Double(-9, 0, 9, 0));
        } else {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 3 * i + phase * 0.4;
                icon.draw(new Line2D.Double(0, 0, Math.cos(a) * 9, Math.sin(a) * 9));
                icon.fill(new Ellipse2D.Double(Math.cos(a) * 9 - 1.8, Math.sin(a) * 9 - 1.8, 3.6, 3.6));
            }
        }

        icon.dispose();

        g.setFont(Theme.font((int) Math.round(9 * s), Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.alpha(Theme.MUTED, 200));
        g.drawString(label, (float) (p.getX() - fm.stringWidth(label) / 2.0), (float) (p.getY() + r + 14 * s));
    }

    private void drawNode(Graphics2D g, SystemNode node) {
        Point2D p = position(node.id());
        double s = scale();
        double grow = hover.getOrDefault(node.id(), 0.0);
        double size = (50 + grow * 6) * s;
        double half = size / 2;
        double x = p.getX();
        double y = p.getY();

        Color status = statusColor(node);
        boolean isSelected = node.id().equals(selected);
        double flashValue = flash.getOrDefault(node.id(), 0.0);

        if (node.alert()) {
            for (int i = 0; i < 3; i++) {
                double t = ((phase * 0.5 + i / 3.0) % 1.0);
                double r = half + t * 34 * s;
                g.setColor(Theme.alpha(Theme.DANGER, (int) (150 * (1 - t))));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            }
        }

        float glowRadius = (float) (size * (1.3 + 0.25 * grow + 0.4 * flashValue));
        int glowAlpha = (int) (35 + 45 * grow + 120 * flashValue + (node.alert() ? 50 : 0));

        g.setPaint(new RadialGradientPaint(p, glowRadius, new float[]{0f, 1f},
                new Color[]{Theme.alpha(status, glowAlpha), Theme.alpha(status, 0)}));
        g.fill(new Ellipse2D.Double(x - glowRadius, y - glowRadius, glowRadius * 2, glowRadius * 2));

        RoundRectangle2D body = new RoundRectangle2D.Double(x - half, y - half, size, size, 14 * s, 14 * s);

        g.setPaint(new GradientPaint(
                (float) x, (float) (y - half), Theme.mix(Theme.PANEL_LIGHT, status, 0.12),
                (float) x, (float) (y + half), Theme.PANEL_DARK
        ));
        g.fill(body);

        if (node.status() == SystemStatus.MAINTENANCE) {
            Graphics2D stripes = (Graphics2D) g.create();
            stripes.clip(body);
            stripes.setColor(new Color(214, 178, 110, 28));
            stripes.setStroke(new BasicStroke((float) (5 * s)));

            for (double i = -size; i < size * 2; i += 12 * s) {
                stripes.draw(new Line2D.Double(x - half + i, y + half, x - half + i + size, y - half));
            }

            stripes.dispose();
        }

        g.setColor(Theme.alpha(status, isSelected ? 255 : 170));
        g.setStroke(new BasicStroke(isSelected ? 2f : 1.3f));
        g.draw(body);

        drawIcon(g, node.id(), x, y, s, Theme.mix(Theme.TEXT, status, 0.35));

        if (isSelected) {
            double r = half + 9 * s;
            Color accent = Theme.accent(role);

            g.setColor(accent);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{8f, 7f}, (float) (15 - (phase * 20) % 15)));
            g.draw(new RoundRectangle2D.Double(x - r, y - r, r * 2, r * 2, 20 * s, 20 * s));

            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double c = 8 * s;
            double o = r + 4 * s;

            g.draw(new Line2D.Double(x - o, y - o, x - o + c, y - o));
            g.draw(new Line2D.Double(x - o, y - o, x - o, y - o + c));
            g.draw(new Line2D.Double(x + o, y - o, x + o - c, y - o));
            g.draw(new Line2D.Double(x + o, y - o, x + o, y - o + c));
            g.draw(new Line2D.Double(x - o, y + o, x - o + c, y + o));
            g.draw(new Line2D.Double(x - o, y + o, x - o, y + o - c));
            g.draw(new Line2D.Double(x + o, y + o, x + o - c, y + o));
            g.draw(new Line2D.Double(x + o, y + o, x + o, y + o - c));
        }

        if (node.status() == SystemStatus.ISOLATED) {
            double r = half + 5 * s;
            g.setColor(new Color(170, 190, 205, 160));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{3f, 4f}, (float) ((phase * 10) % 7)));
            g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        }

        String badge = String.valueOf(node.logCount());
        g.setFont(Theme.mono((int) Math.round(9 * s), Font.BOLD));
        FontMetrics bm = g.getFontMetrics();
        double bw = Math.max(16 * s, bm.stringWidth(badge) + 8 * s);
        double bh = 15 * s;
        RoundRectangle2D badgeShape = new RoundRectangle2D.Double(x + half - bw / 2, y - half - bh / 2,
                bw, bh, bh, bh);

        g.setColor(Theme.PANEL_DARK);
        g.fill(badgeShape);
        g.setColor(Theme.alpha(status, 200));
        g.setStroke(new BasicStroke(1f));
        g.draw(badgeShape);
        g.setColor(Theme.TEXT);
        g.drawString(badge, (float) (badgeShape.getCenterX() - bm.stringWidth(badge) / 2.0),
                (float) (badgeShape.getCenterY() + bm.getAscent() / 2.0 - 1));

        g.setFont(Theme.font((int) Math.round(11 * s), Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(isSelected ? Theme.TEXT : Theme.mix(Theme.TEXT, Theme.MUTED, 0.25));
        g.drawString(node.name(), (float) (x - fm.stringWidth(node.name()) / 2.0),
                (float) (y + half + 16 * s));

        g.setFont(Theme.font((int) Math.round(9 * s), Font.PLAIN));
        fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString(node.description(), (float) (x - fm.stringWidth(node.description()) / 2.0),
                (float) (y + half + 29 * s));

        String tag = switch (node.status()) {
            case ISOLATED -> "ISOLATO";
            case MAINTENANCE -> "MANUTENZIONE";
            case HONEYPOT -> "HONEYPOT";
            case UNKNOWN -> "DISTURBATO";
            default -> node.alert() ? "ALLARME" : null;
        };

        if (tag != null) {
            g.setFont(Theme.font((int) Math.round(9 * s), Font.BOLD));
            FontMetrics tm = g.getFontMetrics();
            double tw = tm.stringWidth(tag) + 14 * s;
            double th = 16 * s;
            double ty = y - half - 24 * s;
            Color tagColor = node.alert() && node.status() == SystemStatus.ONLINE ? Theme.DANGER : status;

            RoundRectangle2D pill = new RoundRectangle2D.Double(x - tw / 2, ty - th / 2, tw, th, th, th);
            g.setColor(Theme.alpha(Theme.mix(Theme.PANEL_DARK, tagColor, 0.3), 235));
            g.fill(pill);
            g.setColor(tagColor);
            g.draw(pill);
            g.drawString(tag, (float) (x - tm.stringWidth(tag) / 2.0), (float) (ty + tm.getAscent() / 2.0 - 1));
        }
    }

    private void drawIcon(Graphics2D g, String id, double x, double y, double s, Color color) {
        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(x, y);
        icon.scale(s, s);
        icon.setColor(color);
        icon.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (id) {
            case "FW" -> {
                Path2D shield = new Path2D.Double();
                shield.moveTo(0, -12);
                shield.lineTo(10, -8);
                shield.curveTo(10, 3, 6, 9, 0, 13);
                shield.curveTo(-6, 9, -10, 3, -10, -8);
                shield.closePath();
                icon.draw(shield);
                icon.draw(new Line2D.Double(-5, 0, -1, 4));
                icon.draw(new Line2D.Double(-1, 4, 6, -4));
            }
            case "VPN" -> {
                icon.draw(new RoundRectangle2D.Double(-9, -2, 18, 13, 4, 4));
                icon.draw(new Arc2D.Double(-6, -12, 12, 16, 0, 180, Arc2D.OPEN));
                icon.fill(new Ellipse2D.Double(-2, 2.5, 4, 4));
            }
            case "MAIL" -> {
                icon.draw(new RoundRectangle2D.Double(-12, -8, 24, 16, 3, 3));
                icon.draw(new Line2D.Double(-12, -8, 0, 2));
                icon.draw(new Line2D.Double(12, -8, 0, 2));
            }
            case "DB" -> {
                icon.draw(new Ellipse2D.Double(-10, -12, 20, 7));
                icon.draw(new Arc2D.Double(-10, -4, 20, 7, 180, 180, Arc2D.OPEN));
                icon.draw(new Arc2D.Double(-10, 4, 20, 7, 180, 180, Arc2D.OPEN));
                icon.draw(new Line2D.Double(-10, -8.5, -10, 7.5));
                icon.draw(new Line2D.Double(10, -8.5, 10, 7.5));
            }
            case "HR" -> {
                icon.draw(new Ellipse2D.Double(-4.5, -12, 9, 9));
                icon.draw(new Arc2D.Double(-10, -1, 20, 22, 0, 180, Arc2D.OPEN));
            }
            default -> {
                for (int i = 0; i < 3; i++) {
                    double row = -10 + i * 7.5;
                    icon.draw(new RoundRectangle2D.Double(-11, row, 22, 5.5, 2, 2));
                    double blink = (Math.sin(phase * 3 + i * 1.7) + 1) / 2;
                    icon.setColor(Theme.alpha(Theme.GOOD, (int) (90 + blink * 165)));
                    icon.fill(new Ellipse2D.Double(6, row + 1.5, 2.6, 2.6));
                    icon.setColor(color);
                }
            }
        }

        icon.dispose();
    }

    private Color statusColor(SystemNode node) {
        return switch (node.status()) {
            case ISOLATED -> new Color(170, 190, 205);
            case MAINTENANCE -> Theme.WARN;
            case HONEYPOT -> Theme.HONEY;
            case UNKNOWN -> new Color(165, 140, 205);
            default -> node.alert() ? Theme.DANGER : Theme.STEEL;
        };
    }

    private static class Packet {

        final String[] path;
        final double speed;
        final Color color;
        final double size;
        double progress;

        Packet(String[] path, double speed, Color color, double size) {
            this.path = path;
            this.speed = speed;
            this.color = color;
            this.size = size;
        }
    }
}
