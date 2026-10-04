package dev.civilizations.simulation.viewer;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.MineSimulationWorld;
import dev.civilizations.simulation.prefab.MinePrefabNavigationScenario;
import dev.civilizations.simulation.prefab.PrefabSimulationModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Visual lab joining the real mine prefab, A* reachability, and Core MinerJob. */
public final class MinePrefabNavigationViewerApp {
    private MinePrefabNavigationViewerApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Frame frame = new Frame();
            frame.setVisible(true);
        });
    }

    private enum ViewMode {
        TOP_DOWN("Draufsicht"), LAYER("Layer"), ISOMETRIC("Isometrisch");
        private final String label;
        ViewMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final class Frame extends JFrame {
        private MinePrefabNavigationScenario scenario = MinePrefabNavigationScenario.create();
        private final Canvas canvas = new Canvas();
        private final JTextArea inspector = new JTextArea();
        private final JLabel layerLabel = new JLabel();
        private final JLabel cutLabel = new JLabel();
        private final JLabel progressLabel = new JLabel();
        private final JButton play = new JButton("Start");
        private final JComboBox<ViewMode> view = new JComboBox<>(ViewMode.values());
        private final Timer timer;
        private boolean running;
        private int layerY;
        private int cutY;

        private Frame() {
            super("Hytale Civ – Mine Prefab Navigation Lab");
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setMinimumSize(new Dimension(1_100, 720));
            setSize(1_420, 880);
            setLocationByPlatform(true);
            setLayout(new BorderLayout());
            resetHeights();
            add(toolbar(), BorderLayout.NORTH);
            add(canvas, BorderLayout.CENTER);
            add(inspectorPanel(), BorderLayout.EAST);
            timer = new Timer(70, event -> {
                if (running && !scenario.step()) {
                    running = false;
                    play.setText("Start");
                }
                refresh();
            });
            timer.start();
            view.setSelectedItem(ViewMode.ISOMETRIC);
            refresh();
        }

        private JPanel toolbar() {
            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            panel.add(new JLabel("Szene: Mine_01 → Berg/Tunnel"));
            panel.add(Box.createHorizontalStrut(12));
            play.addActionListener(event -> {
                running = !running;
                play.setText(running ? "Pause" : "Start");
            });
            panel.add(play);
            JButton step = new JButton("Step");
            step.addActionListener(event -> { scenario.step(); refresh(); });
            panel.add(step);
            JButton reset = new JButton("Reset");
            reset.addActionListener(event -> {
                scenario = MinePrefabNavigationScenario.create();
                running = false;
                play.setText("Start");
                resetHeights();
                refresh();
            });
            panel.add(reset);
            panel.add(Box.createHorizontalStrut(12));
            panel.add(new JLabel("Ansicht:"));
            view.addActionListener(event -> refresh());
            panel.add(view);
            JButton center = new JButton("Zentrieren");
            center.addActionListener(event -> canvas.resetIsoView());
            panel.add(center);

            JButton down = new JButton("Layer Y−");
            down.addActionListener(event -> { layerY = clampY(layerY - 1); refresh(); });
            JButton up = new JButton("Layer Y+");
            up.addActionListener(event -> { layerY = clampY(layerY + 1); refresh(); });
            panel.add(down);
            panel.add(up);
            panel.add(layerLabel);

            panel.add(Box.createHorizontalStrut(10));
            JButton cutDown = new JButton("Cut Y−");
            cutDown.addActionListener(event -> { cutY = clampY(cutY - 1); refresh(); });
            JButton cutUp = new JButton("Cut Y+");
            cutUp.addActionListener(event -> { cutY = clampY(cutY + 1); refresh(); });
            JButton full = new JButton("Voll");
            full.addActionListener(event -> { cutY = sceneMaxY(); refresh(); });
            panel.add(cutDown);
            panel.add(cutUp);
            panel.add(full);
            panel.add(cutLabel);
            panel.add(Box.createHorizontalStrut(12));
            panel.add(progressLabel);
            return panel;
        }

        private JPanel inspectorPanel() {
            inspector.setEditable(false);
            inspector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            inspector.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel panel = new JPanel(new BorderLayout());
            panel.setPreferredSize(new Dimension(390, 700));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 10));
            JLabel title = new JLabel("Mine / Prefab / MinerJob");
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            panel.add(title, BorderLayout.NORTH);
            panel.add(new JScrollPane(inspector), BorderLayout.CENTER);
            return panel;
        }

        private void resetHeights() {
            MinePrefabNavigationScenario.Snapshot s = scenario.snapshot();
            layerY = s.connector().y();
            cutY = clampY(s.segment().start().y() + 3);
        }

        private int clampY(int value) {
            return Math.max(sceneMinY(), Math.min(sceneMaxY(), value));
        }

        private int sceneMinY() {
            MinePrefabNavigationScenario.Snapshot s = scenario.snapshot();
            return Math.min(s.model().blockBounds().minY(), s.tunnelWorld().bounds().minY());
        }

        private int sceneMaxY() {
            MinePrefabNavigationScenario.Snapshot s = scenario.snapshot();
            return Math.max(s.model().blockBounds().maxY(), s.tunnelWorld().bounds().maxY());
        }

        private void refresh() {
            MinePrefabNavigationScenario.Snapshot s = scenario.snapshot();
            canvas.snapshot = s;
            canvas.mode = (ViewMode) view.getSelectedItem();
            canvas.layerY = layerY;
            canvas.cutY = cutY;
            layerLabel.setText(" Y=" + layerY + " ");
            cutLabel.setText(" ≤Y=" + cutY + " ");
            progressLabel.setText("Block " + s.segment().nextBlockIndex() + " / 128 | Supports "
                + s.segment().supportsPlaced() + " / 2");
            MineSimulationWorld.Bounds mountain = s.tunnelWorld().bounds();
            inspector.setText(
                "Quelle\n" + MinePrefabNavigationScenario.PREFAB_PATH + "\n"
                    + MinePrefabNavigationScenario.SUPPORT_PREFAB_PATH + "\n\n"
                    + "Prefab\nBlöcke: " + s.model().cells().size() + "\n"
                    + "Support-Prefab-Blöcke: " + s.supportPrefab().cells().size() + "\n"
                    + "Workplace: " + s.workplace() + "\n"
                    + "Tunnel-Connector: " + s.connector() + "\n"
                    + "A*-Pfad: " + s.pathToConnector().size() + " Zellen\n\n"
                    + "Berg/Felsvolumen\n"
                    + mountain.width() + " × " + mountain.height() + " × " + mountain.depth() + " Blöcke\n"
                    + "Bounds: " + mountain + "\n"
                    + "Cutaway: alles über Y=" + cutY + " ausgeblendet\n\n"
                    + "Aktuell\nPhase: " + s.phase() + "\n"
                    + "MinerJob: " + s.minerState() + "\n"
                    + "Probe/Ziel: " + s.probe() + "\n"
                    + "Tunnelstart: " + s.segment().start() + "\n"
                    + "Fortschritt: " + s.segment().nextBlockIndex() + " / 128\n"
                    + "Supports: " + s.segment().supportsPlaced() + " / 2\n\n"
                    + "Richtung\n" + s.simulationDirection() + "\n"
                    + "Authored im Prefab: " + (s.directionAuthored() ? "JA" : "NEIN") + "\n"
                    + "Ableitung: Placement-Orientation + Prefab-Geometrie\n\n"
                    + "Ansicht\n"
                    + "Cut Y− / Y+ = Berg horizontal aufschneiden\n"
                    + "Voll = gesamte Berghöhe anzeigen\n"
                    + "Draufsicht = höchste sichtbare Zelle ≤ Cut-Y; tiefer = dunkler\n"
                    + "Layer = exakt eine Y-Ebene\n"
                    + "Iso = alles oberhalb Cut-Y abgeschnitten\n\n"
                    + "Iso-Steuerung\n"
                    + "Linksklick + Ziehen = verschieben\n"
                    + "Mausrad = zoomen\n"
                    + "Zentrieren = Ansicht zurücksetzen\n\n"
                    + "Legende\nGrau = echter Mine_01-Block\n"
                    + "Blau = A*-Pfad zum Connector\nRot = aktuelle Probe / Arbeitsziel\n"
                    + "Dunkel = Fels/Berg\n"
                    + "Türkis = erste 4×4-Schneidfläche\n"
                    + "Braun = gesetzter Support\nMagenta = mine_tunnel_connector\n\n"
                    + "A* ist nur ein Reachability-Orakel.\nHytale Seek/NavMesh bleibt Produktionsnavigation."
            );
            canvas.repaint();
        }
    }

    private static final class Canvas extends JPanel {
        private static final int CELL = 26;
        private static final double MIN_ZOOM = 0.35;
        private static final double MAX_ZOOM = 3.0;
        private static final Color PREFAB_COLOR = new Color(150, 150, 150);
        private static final Color ROCK_COLOR = new Color(70, 70, 75);
        private static final Color SUPPORT_COLOR = new Color(145, 95, 55);
        private MinePrefabNavigationScenario.Snapshot snapshot;
        private ViewMode mode = ViewMode.ISOMETRIC;
        private int layerY;
        private int cutY;
        private double isoZoom = 1.0;
        private int isoPanX;
        private int isoPanY;
        private Point dragAnchor;

        private Canvas() {
            setBackground(Color.WHITE);
            MouseAdapter isoNavigation = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (mode == ViewMode.ISOMETRIC && SwingUtilities.isLeftMouseButton(event)) {
                        dragAnchor = event.getPoint();
                    }
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (mode != ViewMode.ISOMETRIC || dragAnchor == null) return;
                    Point current = event.getPoint();
                    isoPanX += current.x - dragAnchor.x;
                    isoPanY += current.y - dragAnchor.y;
                    dragAnchor = current;
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent event) {
                    dragAnchor = null;
                }

                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    if (mode != ViewMode.ISOMETRIC) return;
                    double factor = Math.pow(1.12, -event.getPreciseWheelRotation());
                    isoZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, isoZoom * factor));
                    repaint();
                }
            };
            addMouseListener(isoNavigation);
            addMouseMotionListener(isoNavigation);
            addMouseWheelListener(isoNavigation);
        }

        private void resetIsoView() {
            isoZoom = 1.0;
            isoPanX = 0;
            isoPanY = 0;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (snapshot == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (mode == ViewMode.ISOMETRIC) drawIsoViewport(g);
                else drawFlat(g, mode == ViewMode.LAYER);
            } finally { g.dispose(); }
        }

        private void drawIsoViewport(Graphics2D g) {
            Graphics2D isoGraphics = (Graphics2D) g.create();
            try {
                double centerX = getWidth() / 2.0;
                double centerY = getHeight() / 2.0;
                isoGraphics.translate(isoPanX, isoPanY);
                isoGraphics.translate(centerX, centerY);
                isoGraphics.scale(isoZoom, isoZoom);
                isoGraphics.translate(-centerX, -centerY);
                drawIso(isoGraphics);
            } finally {
                isoGraphics.dispose();
            }
        }

        private void drawFlat(Graphics2D g, boolean layerOnly) {
            PrefabSimulationModel.Bounds bounds = combinedBounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    g.setColor(new Color(238, 238, 238));
                    g.drawRect(sx(x, bounds), sz(z, bounds), CELL, CELL);
                }
            }

            if (layerOnly) drawExactLayer(g, bounds);
            else drawCutTopDown(g, bounds);

            drawFirstFaceFlat(g, bounds, layerOnly);
            drawRoute(g, bounds, layerOnly);
            drawMarkerIfVisible(g, bounds, snapshot.connector(), "C", new Color(170, 40, 160), layerOnly);
            drawMarkerIfVisible(g, bounds, snapshot.probe(), "●", new Color(205, 45, 45), layerOnly);
        }

        private void drawExactLayer(Graphics2D g, PrefabSimulationModel.Bounds bounds) {
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> e : snapshot.model().cells().entrySet()) {
                if (e.getKey().y() == layerY) fill(g, e.getKey(), bounds, PREFAB_COLOR);
            }
            for (Map.Entry<BlockPosition, MineSimulationWorld.Cell> e : snapshot.tunnelWorld().cells().entrySet()) {
                if (e.getKey().y() != layerY || snapshot.model().cellAt(e.getKey()) != null) continue;
                Color color = colorForWorld(e.getValue());
                if (color != null) fill(g, e.getKey(), bounds, color);
            }
        }

        private void drawCutTopDown(Graphics2D g, PrefabSimulationModel.Bounds bounds) {
            int minY = Math.min(bounds.minY(), snapshot.tunnelWorld().bounds().minY());
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Surface surface = topSurface(x, z, minY);
                    if (surface == null) continue;
                    fill(g, surface.position(), bounds, shadeForDepth(surface.color(), cutY - surface.position().y()));
                }
            }
        }

        private Surface topSurface(int x, int z, int minY) {
            for (int y = cutY; y >= minY; y--) {
                BlockPosition position = new BlockPosition(x, y, z);
                if (snapshot.model().cellAt(position) != null) {
                    return new Surface(position, PREFAB_COLOR);
                }
                MineSimulationWorld.Cell worldCell = snapshot.tunnelWorld().get(position);
                if (worldCell == null || worldCell == MineSimulationWorld.Cell.AIR) continue;
                Color color = colorForWorld(worldCell);
                if (color != null) return new Surface(position, color);
            }
            return null;
        }

        private Color shadeForDepth(Color base, int depth) {
            double factor = Math.max(0.42, 1.0 - Math.max(0, depth) * 0.075);
            return new Color(
                (int) Math.round(base.getRed() * factor),
                (int) Math.round(base.getGreen() * factor),
                (int) Math.round(base.getBlue() * factor)
            );
        }

        private Color colorForWorld(MineSimulationWorld.Cell cell) {
            return switch (cell) {
                case SOLID -> ROCK_COLOR;
                case SUPPORT_POST, SUPPORT_BEAM -> SUPPORT_COLOR;
                case AIR -> null;
            };
        }

        private void drawFirstFaceFlat(Graphics2D g, PrefabSimulationModel.Bounds bounds, boolean layerOnly) {
            g.setColor(new Color(30, 180, 190));
            g.setStroke(new BasicStroke(2.5f));
            for (int index = 0; index < 16; index++) {
                BlockPosition position = snapshot.segment().blockAtIndex(index);
                if (layerOnly && position.y() != layerY) continue;
                if (!layerOnly && position.y() > cutY) continue;
                g.drawRect(sx(position.x(), bounds) + 2, sz(position.z(), bounds) + 2, CELL - 4, CELL - 4);
            }
        }

        private void drawRoute(Graphics2D g, PrefabSimulationModel.Bounds bounds, boolean layerOnly) {
            List<BlockPosition> path = snapshot.pathToConnector();
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(55, 115, 210));
            for (int i = 1; i < path.size(); i++) {
                BlockPosition a = path.get(i - 1);
                BlockPosition b = path.get(i);
                if (layerOnly && (a.y() != layerY || b.y() != layerY)) continue;
                if (!layerOnly && (a.y() > cutY || b.y() > cutY)) continue;
                g.drawLine(cx(a.x(), bounds), cz(a.z(), bounds), cx(b.x(), bounds), cz(b.z(), bounds));
            }
        }

        private void drawMarkerIfVisible(
            Graphics2D g,
            PrefabSimulationModel.Bounds bounds,
            BlockPosition position,
            String text,
            Color color,
            boolean layerOnly
        ) {
            if (layerOnly && position.y() != layerY) return;
            if (!layerOnly && position.y() > cutY) return;
            drawMarker(g, bounds, position, text, color);
        }

        private void drawMarker(Graphics2D g, PrefabSimulationModel.Bounds b, BlockPosition p, String text, Color color) {
            int x = cx(p.x(), b), y = cz(p.z(), b);
            g.setColor(color); g.fillOval(x - 9, y - 9, 18, 18);
            g.setColor(Color.WHITE); g.drawString(text, x - 4, y + 5);
        }

        private void fill(Graphics2D g, BlockPosition p, PrefabSimulationModel.Bounds b, Color color) {
            g.setColor(color); g.fillRect(sx(p.x(), b) + 1, sz(p.z(), b) + 1, CELL - 1, CELL - 1);
        }

        private void drawIso(Graphics2D g) {
            List<Cube> cubes = new ArrayList<>();
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> e : snapshot.model().cells().entrySet()) {
                if (e.getKey().y() <= cutY) cubes.add(new Cube(e.getKey(), PREFAB_COLOR));
            }
            for (Map.Entry<BlockPosition, MineSimulationWorld.Cell> e : snapshot.tunnelWorld().cells().entrySet()) {
                BlockPosition position = e.getKey();
                if (position.y() > cutY || snapshot.model().cellAt(position) != null) continue;
                Color color = colorForWorld(e.getValue());
                if (color != null && (e.getValue() != MineSimulationWorld.Cell.SOLID || isExposedRock(position))) {
                    cubes.add(new Cube(position, shadeForDepth(color, cutY - position.y())));
                }
            }
            cubes.sort(Comparator.comparingInt((Cube c) -> c.p.x() + c.p.z())
                .thenComparingInt(c -> c.p.y()));
            for (Cube cube : cubes) drawCube(g, cube.p, cube.color);

            drawFirstFaceIso(g);

            List<BlockPosition> path = snapshot.pathToConnector();
            g.setStroke(new BasicStroke(3f)); g.setColor(new Color(55, 115, 210));
            for (int i = 1; i < path.size(); i++) {
                BlockPosition from = path.get(i - 1);
                BlockPosition to = path.get(i);
                if (from.y() > cutY || to.y() > cutY) continue;
                int[] a = iso(from), b = iso(to);
                g.drawLine(a[0], a[1] - 10, b[0], b[1] - 10);
            }
            if (snapshot.connector().y() <= cutY) {
                int[] c = iso(snapshot.connector());
                g.setColor(new Color(170, 40, 160)); g.fillOval(c[0] - 8, c[1] - 18, 16, 16);
            }
            if (snapshot.probe().y() <= cutY) {
                int[] p = iso(snapshot.probe());
                g.setColor(new Color(205, 45, 45)); g.fillOval(p[0] - 8, p[1] - 18, 16, 16);
            }
        }

        private boolean isExposedRock(BlockPosition p) {
            if (p.y() == cutY) return true;
            int[][] neighbors = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
            };
            for (int[] n : neighbors) {
                BlockPosition neighbor = new BlockPosition(p.x() + n[0], p.y() + n[1], p.z() + n[2]);
                if (neighbor.y() > cutY) return true;
                MineSimulationWorld.Cell cell = snapshot.tunnelWorld().get(neighbor);
                if (cell == null || cell == MineSimulationWorld.Cell.AIR) return true;
            }
            return false;
        }

        private void drawFirstFaceIso(Graphics2D g) {
            g.setColor(new Color(30, 180, 190));
            g.setStroke(new BasicStroke(2.5f));
            for (int index = 0; index < 16; index++) {
                BlockPosition position = snapshot.segment().blockAtIndex(index);
                if (position.y() > cutY) continue;
                int[] point = iso(position);
                g.drawOval(point[0] - 5, point[1] - 14, 10, 10);
            }
        }

        private void drawCube(Graphics2D g, BlockPosition p, Color base) {
            int[] q = iso(p); int w = 18, h = 9, v = 18;
            Polygon top = poly(q[0], q[1]-v, q[0]+w, q[1]-v+h, q[0], q[1]-v+2*h, q[0]-w, q[1]-v+h);
            Polygon left = poly(q[0]-w, q[1]-v+h, q[0], q[1]-v+2*h, q[0], q[1]+2*h, q[0]-w, q[1]+h);
            Polygon right = poly(q[0]+w, q[1]-v+h, q[0], q[1]-v+2*h, q[0], q[1]+2*h, q[0]+w, q[1]+h);
            g.setColor(base.brighter()); g.fillPolygon(top);
            g.setColor(base.darker()); g.fillPolygon(left);
            g.setColor(base); g.fillPolygon(right);
        }

        private PrefabSimulationModel.Bounds combinedBounds() {
            PrefabSimulationModel.Bounds a = snapshot.model().blockBounds();
            MineSimulationWorld.Bounds b = snapshot.tunnelWorld().bounds();
            return new PrefabSimulationModel.Bounds(
                Math.min(a.minX(), b.minX()) - 2, Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()) - 2,
                Math.max(a.maxX(), b.maxX()) + 2, Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()) + 2
            );
        }

        private int sx(int x, PrefabSimulationModel.Bounds b) { return 30 + (x - b.minX()) * CELL; }
        private int sz(int z, PrefabSimulationModel.Bounds b) { return 30 + (z - b.minZ()) * CELL; }
        private int cx(int x, PrefabSimulationModel.Bounds b) { return sx(x, b) + CELL / 2; }
        private int cz(int z, PrefabSimulationModel.Bounds b) { return sz(z, b) + CELL / 2; }
        private int[] iso(BlockPosition p) { return new int[] {430 + (p.x()-p.z())*18, 130 + (p.x()+p.z())*9 - p.y()*18}; }
        private Polygon poly(int... xy) { Polygon p = new Polygon(); for (int i=0;i<xy.length;i+=2) p.addPoint(xy[i],xy[i+1]); return p; }
        private record Cube(BlockPosition p, Color color) {}
        private record Surface(BlockPosition position, Color color) {}
    }
}
