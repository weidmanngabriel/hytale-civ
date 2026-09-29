package dev.civilizations.simulation.viewer;

import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.SimulationMetrics;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Locale;

/**
 * Lightweight desktop viewer for the Hytale-independent simulation runtime.
 *
 * <p>The viewer is presentation only. It advances {@link SimulationRuntime}, renders snapshots
 * and translates mouse input into existing Core commands.</p>
 */
public final class SimulationViewerApp {

    private SimulationViewerApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulationViewerFrame frame = new SimulationViewerFrame();
            frame.setVisible(true);
        });
    }

    private static final class SimulationViewerFrame extends JFrame {

        private static final int FRAME_DELAY_MILLIS = 50;

        private final SimulationCanvas canvas = new SimulationCanvas();
        private final JTextArea residentDetails = detailsArea();
        private final JTextArea metricsDetails = detailsArea();
        private final JLabel clockLabel = new JLabel();
        private final JButton playPauseButton = new JButton("Start");
        private final JComboBox<Speed> speedSelector = new JComboBox<>(Speed.values());
        private final JComboBox<SimulationScenario> scenarioSelector =
            new JComboBox<>(SimulationScenarios.all().toArray(SimulationScenario[]::new));
        private final Timer timer;

        private SimulationScenario selectedScenario = SimulationScenarios.DEMO_SETTLEMENT;
        private SimulationRuntime runtime = selectedScenario.createRuntime();
        private String selectedResidentId;
        private boolean running;

        private SimulationViewerFrame() {
            super("Hytale Civ – Simulation Lab");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1_050, 700));
            setSize(1_280, 800);
            setLocationByPlatform(true);

            canvas.setRuntime(runtime);
            canvas.setSelectionListener(this::selectResident);
            canvas.setManualMoveListener(this::orderManualMove);

            setLayout(new BorderLayout());
            add(createToolbar(), BorderLayout.NORTH);
            add(canvas, BorderLayout.CENTER);
            add(createInspector(), BorderLayout.EAST);

            timer = new Timer(FRAME_DELAY_MILLIS, event -> advanceFrame());
            timer.start();
            refresh();
        }

        private JPanel createToolbar() {
            JPanel toolbar = new JPanel();
            toolbar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            playPauseButton.addActionListener(event -> {
                running = !running;
                playPauseButton.setText(running ? "Pause" : "Start");
            });

            JButton stepButton = new JButton("Step");
            stepButton.addActionListener(event -> {
                runtime.tick();
                refresh();
            });

            JButton resetButton = new JButton("Reset");
            resetButton.addActionListener(event -> reset());

            JButton cancelOrderButton = new JButton("Befehl abbrechen");
            cancelOrderButton.addActionListener(event -> {
                if (selectedResidentId != null) {
                    runtime.cancelManualMove(selectedResidentId);
                    refresh();
                }
            });

            speedSelector.setSelectedItem(Speed.X1);
            scenarioSelector.setSelectedItem(selectedScenario);
            scenarioSelector.addActionListener(event -> {
                SimulationScenario scenario =
                    (SimulationScenario) scenarioSelector.getSelectedItem();
                if (scenario != null && scenario != selectedScenario) {
                    selectedScenario = scenario;
                    reset();
                }
            });

            toolbar.add(new JLabel("Szenario:"));
            toolbar.add(scenarioSelector);
            toolbar.add(Box.createHorizontalStrut(12));
            toolbar.add(playPauseButton);
            toolbar.add(stepButton);
            toolbar.add(new JLabel("Tempo:"));
            toolbar.add(speedSelector);
            toolbar.add(resetButton);
            toolbar.add(cancelOrderButton);
            toolbar.add(Box.createHorizontalStrut(16));
            toolbar.add(clockLabel);

            return toolbar;
        }

        private JPanel createInspector() {
            JPanel inspector = new JPanel();
            inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
            inspector.setPreferredSize(new Dimension(330, 700));
            inspector.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel selectionTitle = new JLabel("Ausgewählter Bewohner");
            selectionTitle.setFont(selectionTitle.getFont().deriveFont(Font.BOLD));
            inspector.add(selectionTitle);
            inspector.add(Box.createVerticalStrut(4));

            JScrollPane residentScroll = new JScrollPane(residentDetails);
            residentScroll.setPreferredSize(new Dimension(310, 220));
            inspector.add(residentScroll);
            inspector.add(Box.createVerticalStrut(12));

            JLabel metricsTitle = new JLabel("Simulation Metrics");
            metricsTitle.setFont(metricsTitle.getFont().deriveFont(Font.BOLD));
            inspector.add(metricsTitle);
            inspector.add(Box.createVerticalStrut(4));

            JScrollPane metricsScroll = new JScrollPane(metricsDetails);
            metricsScroll.setPreferredSize(new Dimension(310, 340));
            inspector.add(metricsScroll);
            inspector.add(Box.createVerticalStrut(12));

            JTextArea help = detailsArea();
            help.setText(
                "Szenarien\n"
                    + "Oben ein Start-Szenario wählen. Wechsel und Reset laden denselben definierten Weltzustand neu.\n\n"
                    + "Bedienung\n"
                    + "Linksklick: Bewohner auswählen\n"
                    + "Rechtsklick: manuelles Ziel setzen\n"
                    + "Mausrad: Zoom\n"
                    + "Step: genau ein Simulations-Tick\n"
                    + "Max: 2.000 Ticks pro UI-Frame"
            );
            help.setRows(6);
            inspector.add(help);

            return inspector;
        }

        private void advanceFrame() {
            if (!running) {
                return;
            }
            Speed speed = (Speed) speedSelector.getSelectedItem();
            runtime.runTicks(speed == null ? 1 : speed.ticksPerFrame);
            refresh();
        }

        private void reset() {
            runtime = selectedScenario.createRuntime();
            selectedResidentId = null;
            canvas.setRuntime(runtime);
            canvas.setSelectedResidentId(null);
            running = false;
            playPauseButton.setText("Start");
            refresh();
        }

        private void selectResident(String residentId) {
            selectedResidentId = residentId;
            canvas.setSelectedResidentId(residentId);
            refresh();
        }

        private void orderManualMove(WorldPosition destination) {
            if (selectedResidentId == null) {
                return;
            }
            runtime.orderManualMove(selectedResidentId, destination);
            refresh();
        }

        private void refresh() {
            SimulationRuntime.WorldSnapshot snapshot = runtime.worldSnapshot();
            canvas.setSnapshot(snapshot);

            clockLabel.setText(String.format(
                Locale.ROOT,
                "Tick %,d  |  %.2f s",
                snapshot.tickCount(),
                snapshot.elapsedSeconds()
            ));

            updateResidentDetails(snapshot);
            updateMetrics(snapshot.metrics());
        }

        private void updateResidentDetails(SimulationRuntime.WorldSnapshot snapshot) {
            if (selectedResidentId == null) {
                residentDetails.setText(
                    "Kein Bewohner ausgewählt.\n\n"
                        + "Linksklick auf einen Bewohner, um seinen Zustand zu sehen."
                );
                return;
            }

            SimulationRuntime.ResidentSnapshot resident = snapshot.residents().stream()
                .filter(candidate -> candidate.id().equals(selectedResidentId))
                .findFirst()
                .orElse(null);

            if (resident == null) {
                selectedResidentId = null;
                canvas.setSelectedResidentId(null);
                residentDetails.setText("Bewohner nicht mehr vorhanden.");
                return;
            }

            residentDetails.setText(
                "ID: " + resident.id() + "\n"
                    + "Beruf: " + resident.profession() + "\n"
                    + "State: " + resident.state() + "\n"
                    + "Autonom: " + resident.autonomousState() + "\n"
                    + "Position: " + position(resident.position()) + "\n"
                    + "Bewegungsziel: "
                    + (resident.movementTarget() == null
                        ? "–"
                        : position(resident.movementTarget()))
                    + "\n"
                    + "Manueller Befehl: "
                    + (resident.manualMovementActive() ? "aktiv" : "nein")
            );
        }

        private void updateMetrics(SimulationMetrics.Snapshot metrics) {
            metricsDetails.setText(
                "Ticks                 " + metrics.ticks() + "\n"
                    + "Decisions             " + metrics.decisions() + "\n"
                    + "  immediate           " + metrics.immediateDecisions() + "\n"
                    + "  retry               " + metrics.retryDecisions() + "\n"
                    + "\n"
                    + "Tree searches          " + metrics.treeSearches() + "\n"
                    + "Construction searches  " + metrics.constructionSearches() + "\n"
                    + "Field searches         " + metrics.fieldSearches() + "\n"
                    + "Movement requests      " + metrics.movementRequests() + "\n"
                    + "Failed plans           " + metrics.failedPlans() + "\n"
                    + "\n"
                    + "Trees felled            " + metrics.treesFelled() + "\n"
                    + "Constructions done     " + metrics.constructionsCompleted() + "\n"
                    + "Farm outputs stored    " + metrics.farmOutputsStored()
            );
        }

        private static JTextArea detailsArea() {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setLineWrap(false);
            area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            return area;
        }

        private static String position(WorldPosition position) {
            return String.format(
                Locale.ROOT,
                "(%.2f, %.2f, %.2f)",
                position.x(),
                position.y(),
                position.z()
            );
        }
    }

    private static final class SimulationCanvas extends JPanel {

        private static final double BASE_PIXELS_PER_UNIT = 42.0;
        private static final int RESIDENT_RADIUS = 8;

        private final Color gridColor = new Color(225, 225, 225);
        private final Color axisColor = new Color(175, 175, 175);
        private final Color treeColor = new Color(45, 125, 60);
        private final Color builderColor = new Color(205, 135, 35);
        private final Color farmerColor = new Color(80, 130, 205);
        private final Color woodcutterColor = new Color(120, 80, 45);
        private final Color fieldColor = new Color(205, 180, 70);
        private final Color selectedColor = new Color(205, 45, 45);

        private SimulationRuntime runtime;
        private SimulationRuntime.WorldSnapshot snapshot;
        private String selectedResidentId;
        private ResidentSelectionListener selectionListener = residentId -> {
        };
        private ManualMoveListener manualMoveListener = destination -> {
        };
        private double zoom = 1.0;

        private SimulationCanvas() {
            setBackground(Color.WHITE);
            setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (snapshot == null) {
                        return;
                    }

                    if (SwingUtilities.isLeftMouseButton(event)) {
                        String residentId = residentAt(event.getX(), event.getY());
                        selectionListener.onSelected(residentId);
                        return;
                    }

                    if (SwingUtilities.isRightMouseButton(event)
                        && selectedResidentId != null) {
                        SimulationRuntime.ResidentSnapshot resident = snapshot.residents().stream()
                            .filter(candidate -> candidate.id().equals(selectedResidentId))
                            .findFirst()
                            .orElse(null);
                        if (resident == null) {
                            return;
                        }

                        WorldPosition world = screenToWorld(
                            event.getX(),
                            event.getY(),
                            resident.position().y()
                        );
                        manualMoveListener.onManualMove(world);
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    double factor = event.getPreciseWheelRotation() < 0.0 ? 1.12 : 0.89;
                    zoom = Math.max(0.45, Math.min(3.5, zoom * factor));
                    repaint();
                }
            };

            addMouseListener(mouse);
            addMouseWheelListener(mouse);
        }

        void setRuntime(SimulationRuntime runtime) {
            this.runtime = runtime;
            setSnapshot(runtime.worldSnapshot());
        }

        void setSnapshot(SimulationRuntime.WorldSnapshot snapshot) {
            this.snapshot = snapshot;
            repaint();
        }

        void setSelectedResidentId(String selectedResidentId) {
            this.selectedResidentId = selectedResidentId;
            repaint();
        }

        void setSelectionListener(ResidentSelectionListener selectionListener) {
            this.selectionListener = selectionListener;
        }

        void setManualMoveListener(ManualMoveListener manualMoveListener) {
            this.manualMoveListener = manualMoveListener;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (snapshot == null) {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );
                drawGrid(g);
                drawFields(g);
                drawConstructionSites(g);
                drawTrees(g);
                drawResidents(g);
                drawLegend(g);
            } finally {
                g.dispose();
            }
        }

        private void drawGrid(Graphics2D g) {
            double scale = scale();
            int halfColumns = (int) Math.ceil(getWidth() / scale / 2.0) + 1;
            int halfRows = (int) Math.ceil(getHeight() / scale / 2.0) + 1;

            g.setStroke(new BasicStroke(1.0f));
            for (int x = -halfColumns; x <= halfColumns; x++) {
                int screenX = screenX(x);
                g.setColor(x == 0 ? axisColor : gridColor);
                g.drawLine(screenX, 0, screenX, getHeight());
            }
            for (int z = -halfRows; z <= halfRows; z++) {
                int screenY = screenY(z);
                g.setColor(z == 0 ? axisColor : gridColor);
                g.drawLine(0, screenY, getWidth(), screenY);
            }
        }

        private void drawFields(Graphics2D g) {
            g.setColor(fieldColor);
            for (SimulationRuntime.FarmFieldSnapshot field : snapshot.farmFields()) {
                int x = screenX(field.position().x());
                int y = screenY(field.position().z());
                int size = Math.max(12, (int) Math.round(scale() * 0.8));
                g.fillRect(x - size / 2, y - size / 2, size, size);
                g.setColor(Color.DARK_GRAY);
                g.drawString("F", x - 4, y + 5);
                g.setColor(fieldColor);
            }
        }

        private void drawConstructionSites(Graphics2D g) {
            for (SimulationRuntime.ConstructionSiteSnapshot site : snapshot.constructionSites()) {
                int x = screenX(site.workPoint().x());
                int y = screenY(site.workPoint().z());
                int size = 22;
                g.setColor(site.completed() ? Color.LIGHT_GRAY : builderColor);
                g.fillRect(x - size / 2, y - size / 2, size, size);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x - size / 2, y - size / 2, size, size);
                g.drawString(site.completed() ? "✓" : "B", x - 4, y + 5);
            }
        }

        private void drawTrees(Graphics2D g) {
            for (SimulationRuntime.TreeSnapshot tree : snapshot.trees()) {
                int x = screenX(tree.position().x() + 0.5);
                int y = screenY(tree.position().z() + 0.5);
                g.setColor(treeColor);
                g.fillOval(x - 9, y - 9, 18, 18);
                g.setColor(Color.WHITE);
                g.drawString("T", x - 4, y + 5);
            }
        }

        private void drawResidents(Graphics2D g) {
            for (SimulationRuntime.ResidentSnapshot resident : snapshot.residents()) {
                int x = screenX(resident.position().x());
                int y = screenY(resident.position().z());
                Color color = professionColor(resident.profession());

                if (resident.id().equals(selectedResidentId)) {
                    g.setColor(selectedColor);
                    g.setStroke(new BasicStroke(3.0f));
                    g.drawOval(
                        x - RESIDENT_RADIUS - 5,
                        y - RESIDENT_RADIUS - 5,
                        (RESIDENT_RADIUS + 5) * 2,
                        (RESIDENT_RADIUS + 5) * 2
                    );
                }

                if (resident.movementTarget() != null) {
                    g.setColor(new Color(120, 120, 120));
                    g.setStroke(new BasicStroke(1.0f));
                    g.drawLine(
                        x,
                        y,
                        screenX(resident.movementTarget().x()),
                        screenY(resident.movementTarget().z())
                    );
                }

                g.setColor(color);
                g.fillOval(
                    x - RESIDENT_RADIUS,
                    y - RESIDENT_RADIUS,
                    RESIDENT_RADIUS * 2,
                    RESIDENT_RADIUS * 2
                );
                g.setColor(Color.BLACK);
                g.drawOval(
                    x - RESIDENT_RADIUS,
                    y - RESIDENT_RADIUS,
                    RESIDENT_RADIUS * 2,
                    RESIDENT_RADIUS * 2
                );
                g.drawString(shortProfession(resident.profession()), x + 11, y + 4);
            }
        }

        private void drawLegend(Graphics2D g) {
            String text =
                "W = Holzfäller   B = Bauarbeiter   Fm = Farmer   T = Baum   B-Quadrat = Baustelle";
            int width = g.getFontMetrics().stringWidth(text) + 16;
            int y = getHeight() - 14;
            g.setColor(new Color(255, 255, 255, 220));
            g.fillRect(8, y - 16, width, 22);
            g.setColor(Color.DARK_GRAY);
            g.drawString(text, 16, y);
        }

        private String residentAt(int mouseX, int mouseY) {
            String nearest = null;
            double nearestDistance = 18.0 * 18.0;
            for (SimulationRuntime.ResidentSnapshot resident : snapshot.residents()) {
                double dx = screenX(resident.position().x()) - mouseX;
                double dy = screenY(resident.position().z()) - mouseY;
                double distance = dx * dx + dy * dy;
                if (distance <= nearestDistance) {
                    nearest = resident.id();
                    nearestDistance = distance;
                }
            }
            return nearest;
        }

        private WorldPosition screenToWorld(int x, int y, double worldY) {
            double worldX = (x - getWidth() / 2.0) / scale();
            double worldZ = (getHeight() / 2.0 - y) / scale();
            return new WorldPosition(worldX, worldY, worldZ);
        }

        private int screenX(double worldX) {
            return (int) Math.round(getWidth() / 2.0 + worldX * scale());
        }

        private int screenY(double worldZ) {
            return (int) Math.round(getHeight() / 2.0 - worldZ * scale());
        }

        private double scale() {
            return BASE_PIXELS_PER_UNIT * zoom;
        }

        private static Color professionColor(Profession profession) {
            return switch (profession) {
                case WOODCUTTER -> new Color(120, 80, 45);
                case CONSTRUCTION_WORKER -> new Color(205, 135, 35);
                case FARMER -> new Color(80, 130, 205);
                case UNEMPLOYED -> Color.GRAY;
            };
        }

        private static String shortProfession(Profession profession) {
            return switch (profession) {
                case WOODCUTTER -> "W";
                case CONSTRUCTION_WORKER -> "B";
                case FARMER -> "Fm";
                case UNEMPLOYED -> "–";
            };
        }
    }

    private enum Speed {
        X1("x1", 1),
        X10("x10", 10),
        X100("x100", 100),
        MAX("Max", 2_000);

        private final String label;
        private final int ticksPerFrame;

        Speed(String label, int ticksPerFrame) {
            this.label = label;
            this.ticksPerFrame = ticksPerFrame;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FunctionalInterface
    private interface ResidentSelectionListener {
        void onSelected(String residentId);
    }

    @FunctionalInterface
    private interface ManualMoveListener {
        void onManualMove(WorldPosition destination);
    }
}
