package dev.civilizations.simulation.viewer;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.simulation.prefab.FarmPrefabNavigationScenario;
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
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Visual lab for prefab-derived geometric reachability. */
public final class PrefabNavigationViewerApp {

    private PrefabNavigationViewerApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PrefabNavigationFrame frame = new PrefabNavigationFrame();
            frame.setVisible(true);
        });
    }

    private static final class PrefabNavigationFrame extends JFrame {
        private final FarmPrefabNavigationScenario.Snapshot scenario =
            FarmPrefabNavigationScenario.create();
        private final PrefabCanvas canvas = new PrefabCanvas(scenario);
        private final JTextArea inspector = new JTextArea();
        private final JLabel stepLabel = new JLabel();
        private final JLabel layerLabel = new JLabel();
        private final JButton playPauseButton = new JButton("Start");
        private final JComboBox<ViewMode> viewSelector = new JComboBox<>(ViewMode.values());
        private final Timer timer;

        private int routeIndex;
        private int layerY = 1;
        private boolean running;

        private PrefabNavigationFrame() {
            super("Hytale Civ – Prefab Navigation Lab");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1_080, 720));
            setSize(1_360, 850);
            setLocationByPlatform(true);
            setLayout(new BorderLayout());
            add(toolbar(), BorderLayout.NORTH);
            add(canvas, BorderLayout.CENTER);
            add(inspectorPanel(), BorderLayout.EAST);

            timer = new Timer(250, event -> {
                if (running) advance();
            });
            timer.start();
            viewSelector.setSelectedItem(ViewMode.ISOMETRIC);
            refresh();
        }

        private JPanel toolbar() {
            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JButton step = new JButton("Step");
            step.addActionListener(event -> advance());
            JButton reset = new JButton("Reset");
            reset.addActionListener(event -> {
                routeIndex = 0;
                running = false;
                playPauseButton.setText("Start");
                refresh();
            });
            playPauseButton.addActionListener(event -> {
                running = !running;
                playPauseButton.setText(running ? "Pause" : "Start");
            });

            JButton layerDown = new JButton("Y−");
            layerDown.addActionListener(event -> {
                layerY = Math.max(scenario.model().blockBounds().minY(), layerY - 1);
                refresh();
            });
            JButton layerUp = new JButton("Y+");
            layerUp.addActionListener(event -> {
                layerY = Math.min(scenario.model().blockBounds().maxY() + 2, layerY + 1);
                refresh();
            });
            viewSelector.addActionListener(event -> refresh());

            panel.add(new JLabel("Prefab: Farm_01"));
            panel.add(Box.createHorizontalStrut(14));
            panel.add(playPauseButton);
            panel.add(step);
            panel.add(reset);
            panel.add(Box.createHorizontalStrut(14));
            panel.add(new JLabel("Ansicht:"));
            panel.add(viewSelector);
            panel.add(layerDown);
            panel.add(layerUp);
            panel.add(layerLabel);
            panel.add(Box.createHorizontalStrut(14));
            panel.add(stepLabel);
            return panel;
        }

        private JPanel inspectorPanel() {
            inspector.setEditable(false);
            inspector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            inspector.setLineWrap(false);
            inspector.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel panel = new JPanel(new BorderLayout());
            panel.setPreferredSize(new Dimension(350, 700));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 10));
            JLabel title = new JLabel("Prefab-Reachability");
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            panel.add(title, BorderLayout.NORTH);
            panel.add(new JScrollPane(inspector), BorderLayout.CENTER);
            return panel;
        }

        private void advance() {
            if (routeIndex >= scenario.combinedPath().size() - 1) {
                running = false;
                playPauseButton.setText("Start");
                return;
            }
            routeIndex++;
            if (routeIndex >= scenario.combinedPath().size() - 1) {
                running = false;
                playPauseButton.setText("Start");
            }
            refresh();
        }

        private void refresh() {
            ViewMode mode = (ViewMode) viewSelector.getSelectedItem();
            canvas.setViewMode(mode == null ? ViewMode.ISOMETRIC : mode);
            canvas.setLayerY(layerY);
            canvas.setRouteIndex(routeIndex);
            layerLabel.setText(" Y=" + layerY + " ");
            stepLabel.setText("Pfad " + (routeIndex + 1) + " / " + scenario.combinedPath().size());

            BlockPosition current = scenario.combinedPath().get(routeIndex);
            int workplaceEnd = scenario.pathToWorkplace().size() - 1;
            String phase = routeIndex < workplaceEnd
                ? "AUSSEN → WORKPLACE"
                : routeIndex == workplaceEnd
                    ? "WORKPLACE ERREICHT"
                    : routeIndex < scenario.combinedPath().size() - 1
                        ? "WORKPLACE → STORAGE"
                        : "STORAGE ERREICHT";

            inspector.setText(
                "Quelle\n" + FarmPrefabNavigationScenario.PREFAB_PATH + "\n\n"
                    + "Echte Prefab-Daten\n"
                    + "Blöcke: " + scenario.model().cells().size() + "\n"
                    + "Tür-Fußzellen: " + scenario.model().doorFeet().size() + "\n"
                    + "Civ-Marker: " + scenario.model().markers().size() + "\n\n"
                    + "Reachability\n"
                    + "Start:     " + scenario.outsideStart() + "\n"
                    + "Tür:       " + scenario.door() + "\n"
                    + "Workplace: " + scenario.workplace() + "\n"
                    + "Storage:   " + scenario.storage() + "\n"
                    + "Außen→Workplace: " + scenario.pathToWorkplace().size() + " Zellen\n"
                    + "Workplace→Storage: " + scenario.pathToStorage().size() + " Zellen\n\n"
                    + "Aktuell\nPhase: " + phase + "\nZelle: " + current + "\n\n"
                    + "Legende\n"
                    + "Grau = echter Prefab-Block\n"
                    + "Braun = echter Türblock, simuliert passierbar\n"
                    + "Blau = kompletter A*-Pfad\nRot = aktuelle Probe\n"
                    + "W = workplace_access\nS = output_storage\nB = building_bounds\n\n"
                    + "WICHTIG\n"
                    + "A* prüft nur geometrische Plausibilität.\n"
                    + "Es simuliert NICHT Hytales Seek/NavMesh/Physik.\n"
                    + "Ein grüner Weg muss später weiterhin in Hytale validiert werden."
            );
            canvas.repaint();
        }
    }

    private static final class PrefabCanvas extends JPanel {
        private static final int CELL = 34;
        private final FarmPrefabNavigationScenario.Snapshot scenario;
        private ViewMode viewMode = ViewMode.ISOMETRIC;
        private int layerY = 1;
        private int routeIndex;

        private PrefabCanvas(FarmPrefabNavigationScenario.Snapshot scenario) {
            this.scenario = scenario;
            setBackground(Color.WHITE);
        }

        void setViewMode(ViewMode viewMode) {
            this.viewMode = viewMode;
        }

        void setLayerY(int layerY) {
            this.layerY = layerY;
        }

        void setRouteIndex(int routeIndex) {
            this.routeIndex = routeIndex;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                switch (viewMode) {
                    case TOP_DOWN -> drawTopDown(g);
                    case LAYER -> drawLayer(g);
                    case ISOMETRIC -> drawIsometric(g);
                }
            } finally {
                g.dispose();
            }
        }

        private void drawTopDown(Graphics2D g) {
            PrefabSimulationModel.Bounds bounds = scenario.model().blockBounds().expand(2, 0);
            drawGrid(g, bounds);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    PrefabSimulationModel.Cell column = columnCell(x, z);
                    if (column != null) fillSquare(g, x, z, cellColor(column));
                }
            }
            drawTopRoute(g);
            drawTopMarkers(g);
        }

        private void drawLayer(Graphics2D g) {
            PrefabSimulationModel.Bounds bounds = scenario.model().blockBounds().expand(2, 0);
            drawGrid(g, bounds);
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> entry : scenario.model().cells().entrySet()) {
                if (entry.getKey().y() == layerY) {
                    fillSquare(g, entry.getKey().x(), entry.getKey().z(), cellColor(entry.getValue()));
                }
            }
            drawTopRoute(g);
            drawTopMarkers(g);
        }

        private void drawGrid(Graphics2D g, PrefabSimulationModel.Bounds bounds) {
            g.setStroke(new BasicStroke(1.0f));
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    g.setColor(new Color(232, 232, 232));
                    g.drawRect(topX(x), topY(z), CELL, CELL);
                }
            }
        }

        private void fillSquare(Graphics2D g, int x, int z, Color color) {
            int sx = topX(x);
            int sy = topY(z);
            g.setColor(color);
            g.fillRect(sx + 1, sy + 1, CELL - 1, CELL - 1);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(sx, sy, CELL, CELL);
        }

        private void drawTopRoute(Graphics2D g) {
            List<BlockPosition> path = scenario.combinedPath();
            g.setStroke(new BasicStroke(3.0f));
            g.setColor(new Color(55, 115, 210));
            for (int i = 1; i < path.size(); i++) {
                g.drawLine(topCenterX(path.get(i - 1).x()), topCenterY(path.get(i - 1).z()),
                    topCenterX(path.get(i).x()), topCenterY(path.get(i).z()));
            }
            BlockPosition current = path.get(routeIndex);
            g.setColor(new Color(205, 45, 45));
            g.fillOval(topCenterX(current.x()) - 7, topCenterY(current.z()) - 7, 14, 14);
        }

        private void drawTopMarkers(Graphics2D g) {
            for (PrefabSimulationModel.Marker marker : scenario.model().markers()) {
                int x = (int) Math.floor(marker.bounds().centerX());
                int z = (int) Math.floor(marker.bounds().centerZ());
                int sx = topCenterX(x);
                int sy = topCenterY(z);
                g.setColor(markerColor(marker.type()));
                g.fillOval(sx - 10, sy - 10, 20, 20);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(markerLabel(marker.type())), sx - 4, sy + 5);
            }
        }

        private void drawIsometric(Graphics2D g) {
            List<Map.Entry<BlockPosition, PrefabSimulationModel.Cell>> entries =
                new ArrayList<>(scenario.model().cells().entrySet());
            entries.sort(Comparator
                .comparingInt((Map.Entry<BlockPosition, PrefabSimulationModel.Cell> entry) ->
                    entry.getKey().x() + entry.getKey().z())
                .thenComparingInt(entry -> entry.getKey().y())
                .thenComparingInt(entry -> entry.getKey().x()));
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> entry : entries) {
                drawIsoCube(g, entry.getKey(), cellColor(entry.getValue()));
            }

            List<BlockPosition> path = scenario.combinedPath();
            g.setStroke(new BasicStroke(3.0f));
            g.setColor(new Color(55, 115, 210));
            for (int i = 1; i < path.size(); i++) {
                int[] a = iso(path.get(i - 1));
                int[] b = iso(path.get(i));
                g.drawLine(a[0], a[1] - 10, b[0], b[1] - 10);
            }
            int[] probe = iso(path.get(routeIndex));
            g.setColor(new Color(205, 45, 45));
            g.fillOval(probe[0] - 7, probe[1] - 17, 14, 14);

            for (PrefabSimulationModel.Marker marker : scenario.model().markers()) {
                int[] p = iso(
                    (int) Math.floor(marker.bounds().centerX()),
                    (int) Math.floor(marker.bounds().minY()),
                    (int) Math.floor(marker.bounds().centerZ())
                );
                g.setColor(markerColor(marker.type()));
                g.fillOval(p[0] - 9, p[1] - 20, 18, 18);
                g.setColor(Color.WHITE);
                g.drawString(String.valueOf(markerLabel(marker.type())), p[0] - 4, p[1] - 7);
            }
        }

        private void drawIsoCube(Graphics2D g, BlockPosition position, Color base) {
            int[] p = iso(position);
            int w = 26;
            int h = 13;
            int v = 25;
            Polygon top = polygon(p[0], p[1] - v, p[0] + w, p[1] - v + h,
                p[0], p[1] - v + h * 2, p[0] - w, p[1] - v + h);
            Polygon left = polygon(p[0] - w, p[1] - v + h, p[0], p[1] - v + h * 2,
                p[0], p[1] + h * 2, p[0] - w, p[1] + h);
            Polygon right = polygon(p[0] + w, p[1] - v + h, p[0], p[1] - v + h * 2,
                p[0], p[1] + h * 2, p[0] + w, p[1] + h);
            g.setColor(base.brighter());
            g.fillPolygon(top);
            g.setColor(base.darker());
            g.fillPolygon(left);
            g.setColor(base);
            g.fillPolygon(right);
            g.setColor(new Color(70, 70, 70));
            g.drawPolygon(top);
            g.drawPolygon(left);
            g.drawPolygon(right);
        }

        private PrefabSimulationModel.Cell columnCell(int x, int z) {
            PrefabSimulationModel.Cell result = null;
            for (Map.Entry<BlockPosition, PrefabSimulationModel.Cell> entry : scenario.model().cells().entrySet()) {
                if (entry.getKey().x() == x && entry.getKey().z() == z) {
                    if (entry.getValue() == PrefabSimulationModel.Cell.DOOR) return PrefabSimulationModel.Cell.DOOR;
                    result = PrefabSimulationModel.Cell.SOLID;
                }
            }
            return result;
        }

        private int topX(int x) {
            PrefabSimulationModel.Bounds bounds = scenario.model().blockBounds().expand(2, 0);
            int cellsWide = bounds.maxX() - bounds.minX() + 1;
            return getWidth() / 2 - cellsWide * CELL / 2 + (x - bounds.minX()) * CELL;
        }

        private int topY(int z) {
            PrefabSimulationModel.Bounds bounds = scenario.model().blockBounds().expand(2, 0);
            int cellsDeep = bounds.maxZ() - bounds.minZ() + 1;
            return getHeight() / 2 - cellsDeep * CELL / 2 + (z - bounds.minZ()) * CELL;
        }

        private int topCenterX(int x) {
            return topX(x) + CELL / 2;
        }

        private int topCenterY(int z) {
            return topY(z) + CELL / 2;
        }

        private int[] iso(BlockPosition position) {
            return iso(position.x(), position.y(), position.z());
        }

        private int[] iso(int x, int y, int z) {
            int sx = getWidth() / 2 + (x - z) * 27;
            int sy = getHeight() / 2 + 55 + (x + z) * 13 - y * 28;
            return new int[] {sx, sy};
        }

        private static Polygon polygon(int... coordinates) {
            Polygon polygon = new Polygon();
            for (int i = 0; i < coordinates.length; i += 2) {
                polygon.addPoint(coordinates[i], coordinates[i + 1]);
            }
            return polygon;
        }

        private static Color cellColor(PrefabSimulationModel.Cell cell) {
            return cell == PrefabSimulationModel.Cell.DOOR
                ? new Color(150, 100, 55)
                : new Color(145, 150, 158);
        }

        private static Color markerColor(String type) {
            return switch (type) {
                case "workplace_access" -> new Color(55, 125, 210);
                case "output_storage" -> new Color(70, 150, 90);
                default -> new Color(120, 90, 165);
            };
        }

        private static char markerLabel(String type) {
            return switch (type) {
                case "workplace_access" -> 'W';
                case "output_storage" -> 'S';
                default -> 'B';
            };
        }
    }

    private enum ViewMode {
        TOP_DOWN("Draufsicht"),
        LAYER("Layer"),
        ISOMETRIC("Isometrisch");

        private final String label;

        ViewMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
