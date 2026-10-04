package dev.civilizations.simulation.viewer;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.MineSimulationWorld;
import dev.civilizations.simulation.prefab.MinePrefabNavigationScenario;
import dev.civilizations.simulation.prefab.PrefabSimulationModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
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
import java.awt.event.ActionEvent;
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

        ViewMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
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
            step.addActionListener(event -> {
                scenario.step();
                refresh();
            });
            panel.add(step);

            JButton reset = new JButton("Reset");
            reset.addActionListener(event -> {
                scenario = MinePrefabNavigationScenario.create();
                running = false;
                play.setText("Start");
                resetHeights();
                canvas.resetOrbitView();
                refresh();
            });
            panel.add(reset);

            panel.add(Box.createHorizontalStrut(12));
            panel.add(new JLabel("Ansicht:"));
            view.addActionListener(event -> refresh());
            panel.add(view);

            JButton center = new JButton("Kamera Reset");
            center.addActionListener(event -> canvas.resetOrbitView());
            panel.add(center);

            JButton down = new JButton("Layer Y−");
            down.addActionListener(event -> {
                layerY = clampY(layerY - 1);
                refresh();
            });
            JButton up = new JButton("Layer Y+");
            up.addActionListener(event -> {
                layerY = clampY(layerY + 1);
                refresh();
            });
            panel.add(down);
            panel.add(up);
            panel.add(layerLabel);

            panel.add(Box.createHorizontalStrut(10));
            JButton cutDown = new JButton("Cut Y−");
            cutDown.addActionListener(event -> {
                cutY = clampY(cutY - 1);
                refresh();
            });
            JButton cutUp = new JButton("Cut Y+");
            cutUp.addActionListener(event -> {
                cutY = clampY(cutY + 1);
                refresh();
            });
            JButton full = new JButton("Voll");
            full.addActionListener(event -> {
                cutY = sceneMaxY();
                refresh();
            });
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
            progressLabel.setText(
                "Block " + s.segment().nextBlockIndex() + " / 128 | Supports "
                    + s.segment().supportsPlaced() + " / 2"
            );

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
                    + "Worker (rot): " + s.probe() + "\n"
                    + "Arbeitsziel: " + s.lastAction() + "\n"
                    + "Reichweite: " + MinePrefabNavigationScenario.WORK_REACH_BLOCKS + " Blöcke\n"
                    + "Tunnelboden/Feet-Y: " + s.segment().start().y() + "\n"
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
                    + "Iso = alle nicht-leeren Blöcke ≤ Cut-Y, darüber hart abgeschnitten\n\n"
                    + "Iso-Steuerung\n"
                    + "W / S = vor / zurück\n"
                    + "A / D = links / rechts\n"
                    + "Q / E = runter / hoch\n"
                    + "Linksklick + Ziehen = Bildschirm verschieben\n"
                    + "Rechtsklick + horizontal = um Berg drehen\n"
                    + "Rechtsklick + vertikal = hoch/runter kippen\n"
                    + "Mausrad = zoomen\n"
                    + "Kamera Reset = Position + Winkel zurücksetzen\n"
                    + String.format(
                        "Kamera: yaw %.0f° | pitch %.0f° | offset %.1f/%.1f/%.1f\n\n",
                        Math.toDegrees(canvas.cameraYaw),
                        Math.toDegrees(canvas.cameraPitch),
                        canvas.cameraOffsetX,
                        canvas.cameraOffsetY,
                        canvas.cameraOffsetZ
                    )
                    + "Legende\nGrau = echter Mine_01-Block\n"
                    + "Blau = A*-Pfad zum Connector\nRot = Worker/Fußposition\n"
                    + "Dunkel = Fels/Berg\n"
                    + "Türkis = noch vorhandene Blöcke der ersten Schneidfläche\n"
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
        private static final double MIN_PITCH = Math.toRadians(10);
        private static final double MAX_PITCH = Math.toRadians(80);
        private static final double DEFAULT_YAW = Math.toRadians(45);
        private static final double DEFAULT_PITCH = Math.toRadians(35);
        private static final double ORBIT_SENSITIVITY = 0.010;
        private static final double MOVE_STEP = 0.75;
        private static final double ISO_SCALE = 24.0;

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
        private double cameraYaw = DEFAULT_YAW;
        private double cameraPitch = DEFAULT_PITCH;
        private double cameraOffsetX;
        private double cameraOffsetY;
        private double cameraOffsetZ;
        private Point dragAnchor;
        private DragMode dragMode = DragMode.NONE;

        private enum DragMode {
            NONE,
            PAN,
            ORBIT
        }

        private Canvas() {
            setBackground(Color.WHITE);
            installKeyboardNavigation();

            MouseAdapter isoNavigation = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (mode != ViewMode.ISOMETRIC) return;
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        dragMode = DragMode.PAN;
                        dragAnchor = event.getPoint();
                    } else if (SwingUtilities.isRightMouseButton(event)) {
                        dragMode = DragMode.ORBIT;
                        dragAnchor = event.getPoint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (mode != ViewMode.ISOMETRIC || dragAnchor == null) return;
                    Point current = event.getPoint();
                    int dx = current.x - dragAnchor.x;
                    int dy = current.y - dragAnchor.y;

                    if (dragMode == DragMode.PAN) {
                        isoPanX += dx;
                        isoPanY += dy;
                    } else if (dragMode == DragMode.ORBIT) {
                        cameraYaw = normalizeAngle(cameraYaw + dx * ORBIT_SENSITIVITY);
                        cameraPitch = clamp(
                            cameraPitch - dy * ORBIT_SENSITIVITY,
                            MIN_PITCH,
                            MAX_PITCH
                        );
                    }

                    dragAnchor = current;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    dragAnchor = null;
                    dragMode = DragMode.NONE;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    if (mode != ViewMode.ISOMETRIC) return;
                    double factor = Math.pow(1.12, -event.getPreciseWheelRotation());
                    isoZoom = clamp(isoZoom * factor, MIN_ZOOM, MAX_ZOOM);
                    repaint();
                }
            };
            addMouseListener(isoNavigation);
            addMouseMotionListener(isoNavigation);
            addMouseWheelListener(isoNavigation);
        }

        private void installKeyboardNavigation() {
            bindKey("W", "camera-forward", () -> moveCamera(0, 1, 0));
            bindKey("S", "camera-back", () -> moveCamera(0, -1, 0));
            bindKey("A", "camera-left", () -> moveCamera(-1, 0, 0));
            bindKey("D", "camera-right", () -> moveCamera(1, 0, 0));
            bindKey("Q", "camera-down", () -> moveCamera(0, 0, -1));
            bindKey("E", "camera-up", () -> moveCamera(0, 0, 1));
        }

        private void bindKey(String key, String actionName, Runnable action) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), actionName);
            getActionMap().put(actionName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (mode != ViewMode.ISOMETRIC) return;
                    action.run();
                }
            });
        }

        private void moveCamera(double right, double forward, double vertical) {
            double sinYaw = Math.sin(cameraYaw);
            double cosYaw = Math.cos(cameraYaw);
            cameraOffsetX += (cosYaw * right + sinYaw * forward) * MOVE_STEP;
            cameraOffsetZ += (-sinYaw * right + cosYaw * forward) * MOVE_STEP;
            cameraOffsetY += vertical * MOVE_STEP;
            repaint();
        }

        private void resetOrbitView() {
            isoZoom = 1.0;
            isoPanX = 0;
            isoPanY = 0;
            cameraYaw = DEFAULT_YAW;
            cameraPitch = DEFAULT_PITCH;
            cameraOffsetX = 0;
            cameraOffsetY = 0;
            cameraOffsetZ = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (snapshot == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (mode == ViewMode.ISOMETRIC) drawIsoViewport(g);
                else drawFlat(g, mode == ViewMode.LAYER);
            } finally {
                g.dispose();
            }
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
                if (snapshot.model().cellAt(position) != null) return new Surface(position, PREFAB_COLOR);
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

        private boolean isStillSolid(BlockPosition position) {
            return snapshot.tunnelWorld().get(position) == MineSimulationWorld.Cell.SOLID;
        }

        private void drawFirstFaceFlat(Graphics2D g, PrefabSimulationModel.Bounds bounds, boolean layerOnly) {
            g.setColor(new Color(30, 180, 190));
            g.setStroke(new BasicStroke(2.5f));
            for (int index = 0; index < 16; index++) {
                BlockPosition position = snapshot.segment().blockAtIndex(index);
                if (!isStillSolid(position)) continue;
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

        private void drawMarker(Graphics2D g, PrefabSimulationModel.Bounds bounds, BlockPosition p, String text, Color color) {
            int x = cx(p.x(), bounds);
            int y = cz(p.z(), bounds);
            g.setColor(color);
            g.fillOval(x - 9, y - 9, 18, 18);
            g.setColor(Color.WHITE);
            g.drawString(text, x - 4, y + 5);
        }

        private void fill(Graphics2D g, BlockPosition p, PrefabSimulationModel.Bounds b, Color color) {
            g.setColor(color);
            g.fillRect(sx(p.x(), b) + 1, sz(p.z(), b) + 1, CELL - 1, CELL - 1);
        }

        private void drawIso(Graphics2D g) {
            List<Face> faces = new ArrayList<>();
            for (Cube cube : visibleCubes()) {
                faces.addAll(cubeFaces(cube.p, cube.color));
            }
            faces.sort(Comparator.comparingDouble(Face::depth).reversed());
            for (Face face : faces) {
                g.setColor(face.color);
                g.fillPolygon(face.polygon);
                g.setColor(face.color.darker());
                g.drawPolygon(face.polygon);
            }

            drawFirstFaceIso(g);
            drawIsoRoute(g);
            drawIsoMarker(g, snapshot.connector(), new Color(170, 40, 160));
            drawIsoMarker(g, snapshot.probe(), new Color(205, 45, 45));
        }

        private List<Cube> visibleCubes() {
            List<Cube> cubes = new ArrayList<>();
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> e : snapshot.model().cells().entrySet()) {
                if (e.getKey().y() <= cutY) cubes.add(new Cube(e.getKey(), PREFAB_COLOR));
            }
            for (Map.Entry<BlockPosition, MineSimulationWorld.Cell> e : snapshot.tunnelWorld().cells().entrySet()) {
                BlockPosition position = e.getKey();
                if (position.y() > cutY || snapshot.model().cellAt(position) != null) continue;
                Color color = colorForWorld(e.getValue());
                if (color != null) {
                    cubes.add(new Cube(position, shadeForDepth(color, cutY - position.y())));
                }
            }
            return cubes;
        }

        private void drawIsoRoute(Graphics2D g) {
            List<BlockPosition> path = snapshot.pathToConnector();
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(55, 115, 210));
            for (int i = 1; i < path.size(); i++) {
                BlockPosition from = path.get(i - 1);
                BlockPosition to = path.get(i);
                if (from.y() > cutY || to.y() > cutY) continue;
                ScreenPoint a = projectCenter(from);
                ScreenPoint b = projectCenter(to);
                g.drawLine(a.x, a.y - 8, b.x, b.y - 8);
            }
        }

        private void drawIsoMarker(Graphics2D g, BlockPosition position, Color color) {
            if (position.y() > cutY) return;
            ScreenPoint point = projectCenter(position);
            g.setColor(color);
            g.fillOval(point.x - 8, point.y - 16, 16, 16);
        }

        private void drawFirstFaceIso(Graphics2D g) {
            g.setColor(new Color(30, 180, 190));
            g.setStroke(new BasicStroke(2.5f));
            for (int index = 0; index < 16; index++) {
                BlockPosition position = snapshot.segment().blockAtIndex(index);
                if (!isStillSolid(position) || position.y() > cutY) continue;
                ScreenPoint point = projectCenter(position);
                g.drawOval(point.x - 5, point.y - 10, 10, 10);
            }
        }

        private List<Face> cubeFaces(BlockPosition p, Color base) {
            double[][] corners = {
                {p.x(), p.y(), p.z()},
                {p.x() + 1, p.y(), p.z()},
                {p.x() + 1, p.y() + 1, p.z()},
                {p.x(), p.y() + 1, p.z()},
                {p.x(), p.y(), p.z() + 1},
                {p.x() + 1, p.y(), p.z() + 1},
                {p.x() + 1, p.y() + 1, p.z() + 1},
                {p.x(), p.y() + 1, p.z() + 1}
            };
            ScreenPoint[] projected = new ScreenPoint[corners.length];
            for (int i = 0; i < corners.length; i++) {
                projected[i] = project(corners[i][0], corners[i][1], corners[i][2]);
            }

            int[][] faceIndices = {
                {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 4, 7, 3},
                {1, 5, 6, 2}, {3, 2, 6, 7}, {0, 1, 5, 4}
            };
            double[][] normals = {
                {0, 0, -1}, {0, 0, 1}, {-1, 0, 0},
                {1, 0, 0}, {0, 1, 0}, {0, -1, 0}
            };

            List<Face> faces = new ArrayList<>();
            for (int i = 0; i < faceIndices.length; i++) {
                int[] indices = faceIndices[i];
                if (faceFacing(normals[i][0], normals[i][1], normals[i][2]) >= 0.0) continue;
                double depth = 0;
                Polygon polygon = new Polygon();
                for (int index : indices) {
                    ScreenPoint point = projected[index];
                    depth += point.depth;
                    polygon.addPoint(point.x, point.y);
                }
                faces.add(new Face(polygon, depth / indices.length, faceColor(base, normals[i])));
            }
            return faces;
        }

        private Color faceColor(Color base, double[] normal) {
            double brightness;
            if (normal[1] > 0.5) brightness = 1.18;
            else if (normal[0] + normal[2] > 0.5) brightness = 1.0;
            else brightness = 0.78;
            return scaleColor(base, brightness);
        }

        private Color scaleColor(Color base, double factor) {
            return new Color(
                clampColor((int) Math.round(base.getRed() * factor)),
                clampColor((int) Math.round(base.getGreen() * factor)),
                clampColor((int) Math.round(base.getBlue() * factor))
            );
        }

        private int clampColor(int value) {
            return Math.max(0, Math.min(255, value));
        }

        private double faceFacing(double nx, double ny, double nz) {
            double[] view = viewDirection();
            return nx * view[0] + ny * view[1] + nz * view[2];
        }

        private double[] viewDirection() {
            double sinYaw = Math.sin(cameraYaw);
            double cosYaw = Math.cos(cameraYaw);
            double sinPitch = Math.sin(cameraPitch);
            double cosPitch = Math.cos(cameraPitch);
            return new double[] {sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch};
        }

        private ScreenPoint projectCenter(BlockPosition p) {
            return project(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
        }

        private ScreenPoint project(double x, double y, double z) {
            double[] center = sceneCenter();
            double dx = x - center[0];
            double dy = y - center[1];
            double dz = z - center[2];
            double sinYaw = Math.sin(cameraYaw);
            double cosYaw = Math.cos(cameraYaw);
            double sinPitch = Math.sin(cameraPitch);
            double cosPitch = Math.cos(cameraPitch);
            double right = cosYaw * dx - sinYaw * dz;
            double forward = sinYaw * dx + cosYaw * dz;
            double screenVertical = -dy * cosPitch + forward * sinPitch;
            double depth = forward * cosPitch - dy * sinPitch;
            int screenX = (int) Math.round(getWidth() / 2.0 + right * ISO_SCALE);
            int screenY = (int) Math.round(getHeight() / 2.0 + screenVertical * ISO_SCALE);
            return new ScreenPoint(screenX, screenY, depth);
        }

        private double[] sceneCenter() {
            PrefabSimulationModel.Bounds b = combinedBounds();
            return new double[] {
                (b.minX() + b.maxX() + 1) / 2.0 + cameraOffsetX,
                (b.minY() + Math.min(b.maxY(), cutY) + 1) / 2.0 + cameraOffsetY,
                (b.minZ() + b.maxZ() + 1) / 2.0 + cameraOffsetZ
            };
        }

        private PrefabSimulationModel.Bounds combinedBounds() {
            PrefabSimulationModel.Bounds a = snapshot.model().blockBounds();
            MineSimulationWorld.Bounds b = snapshot.tunnelWorld().bounds();
            return new PrefabSimulationModel.Bounds(
                Math.min(a.minX(), b.minX()) - 2,
                Math.min(a.minY(), b.minY()),
                Math.min(a.minZ(), b.minZ()) - 2,
                Math.max(a.maxX(), b.maxX()) + 2,
                Math.max(a.maxY(), b.maxY()),
                Math.max(a.maxZ(), b.maxZ()) + 2
            );
        }

        private int sx(int x, PrefabSimulationModel.Bounds b) {
            return 30 + (x - b.minX()) * CELL;
        }

        private int sz(int z, PrefabSimulationModel.Bounds b) {
            return 30 + (z - b.minZ()) * CELL;
        }

        private int cx(int x, PrefabSimulationModel.Bounds b) {
            return sx(x, b) + CELL / 2;
        }

        private int cz(int z, PrefabSimulationModel.Bounds b) {
            return sz(z, b) + CELL / 2;
        }

        private static double normalizeAngle(double angle) {
            double full = Math.PI * 2.0;
            double normalized = angle % full;
            return normalized < 0 ? normalized + full : normalized;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }

        private record Cube(BlockPosition p, Color color) {
        }

        private record Surface(BlockPosition position, Color color) {
        }

        private record ScreenPoint(int x, int y, double depth) {
        }

        private record Face(Polygon polygon, double depth, Color color) {
        }
    }
}
