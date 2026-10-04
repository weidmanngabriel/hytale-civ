package dev.civilizations.simulation.viewer;

import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.MineSupportFrame;
import dev.civilizations.core.MineTuning;
import dev.civilizations.core.Profession;
import dev.civilizations.core.WorldPosition;
import dev.civilizations.simulation.MineSimulationWorld;
import dev.civilizations.simulation.SimulationMetrics;
import dev.civilizations.simulation.SimulationRuntime;
import dev.civilizations.simulation.SimulationScenario;
import dev.civilizations.simulation.SimulationScenarios;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight desktop viewer for the Hytale-independent simulation runtime.
 *
 * <p>The viewer is presentation only. It advances {@link SimulationRuntime}, renders snapshots
 * and translates mouse input into existing Core commands. Mine views render the same voxel
 * snapshot that automated scenario tests observe; they do not own mining rules.</p>
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
        private final JTextArea mineDetails = detailsArea();
        private final JTextArea metricsDetails = detailsArea();
        private final JLabel clockLabel = new JLabel();
        private final JLabel layerLabel = new JLabel("Y –");
        private final JButton playPauseButton = new JButton("Start");
        private final JButton layerDownButton = new JButton("Y−");
        private final JButton layerUpButton = new JButton("Y+");
        private final JCheckBox expectedOverlay = new JCheckBox("Soll", true);
        private final JComboBox<Speed> speedSelector = new JComboBox<>(Speed.values());
        private final JComboBox<ViewMode> viewSelector = new JComboBox<>(ViewMode.values());
        private final JComboBox<SimulationScenario> scenarioSelector =
            new JComboBox<>(SimulationScenarios.all().toArray(SimulationScenario[]::new));
        private final Timer timer;

        private SimulationScenario selectedScenario = SimulationScenarios.DEMO_SETTLEMENT;
        private SimulationRuntime runtime = selectedScenario.createRuntime();
        private String selectedResidentId;
        private int selectedLayerY;
        private boolean running;

        private SimulationViewerFrame() {
            super("Hytale Civ – Simulation Lab");
            setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1_100, 720));
            setSize(1_380, 860);
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
            viewSelector.setSelectedItem(ViewMode.TOP_DOWN);
            viewSelector.addActionListener(event -> {
                ViewMode mode = (ViewMode) viewSelector.getSelectedItem();
                canvas.setViewMode(mode == null ? ViewMode.TOP_DOWN : mode);
            });
            expectedOverlay.addActionListener(event ->
                canvas.setShowExpected(expectedOverlay.isSelected())
            );
            layerDownButton.addActionListener(event -> changeLayer(-1));
            layerUpButton.addActionListener(event -> changeLayer(1));

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
            toolbar.add(Box.createHorizontalStrut(10));
            toolbar.add(playPauseButton);
            toolbar.add(stepButton);
            toolbar.add(new JLabel("Tempo:"));
            toolbar.add(speedSelector);
            toolbar.add(resetButton);
            toolbar.add(Box.createHorizontalStrut(10));
            toolbar.add(new JLabel("Ansicht:"));
            toolbar.add(viewSelector);
            toolbar.add(layerDownButton);
            toolbar.add(layerLabel);
            toolbar.add(layerUpButton);
            toolbar.add(expectedOverlay);
            toolbar.add(Box.createHorizontalStrut(10));
            toolbar.add(cancelOrderButton);
            toolbar.add(Box.createHorizontalStrut(12));
            toolbar.add(clockLabel);

            return toolbar;
        }

        private JPanel createInspector() {
            JPanel inspector = new JPanel();
            inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
            inspector.setPreferredSize(new Dimension(350, 740));
            inspector.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel selectionTitle = new JLabel("Ausgewählter Bewohner");
            selectionTitle.setFont(selectionTitle.getFont().deriveFont(Font.BOLD));
            inspector.add(selectionTitle);
            inspector.add(Box.createVerticalStrut(4));

            JScrollPane residentScroll = new JScrollPane(residentDetails);
            residentScroll.setPreferredSize(new Dimension(330, 180));
            inspector.add(residentScroll);
            inspector.add(Box.createVerticalStrut(10));

            JLabel mineTitle = new JLabel("Mine / aktueller Intent");
            mineTitle.setFont(mineTitle.getFont().deriveFont(Font.BOLD));
            inspector.add(mineTitle);
            inspector.add(Box.createVerticalStrut(4));

            JScrollPane mineScroll = new JScrollPane(mineDetails);
            mineScroll.setPreferredSize(new Dimension(330, 200));
            inspector.add(mineScroll);
            inspector.add(Box.createVerticalStrut(10));

            JLabel metricsTitle = new JLabel("Simulation Metrics");
            metricsTitle.setFont(metricsTitle.getFont().deriveFont(Font.BOLD));
            inspector.add(metricsTitle);
            inspector.add(Box.createVerticalStrut(4));

            JScrollPane metricsScroll = new JScrollPane(metricsDetails);
            metricsScroll.setPreferredSize(new Dimension(330, 220));
            inspector.add(metricsScroll);
            inspector.add(Box.createVerticalStrut(10));

            JTextArea help = detailsArea();
            help.setText(
                "Bedienung\n"
                    + "Step: genau ein 50-ms-Tick\n"
                    + "Top-Down: Draufsicht\n"
                    + "Layer: ausgewählte Y-Schicht\n"
                    + "Isometrisch: feste 3D-Cutaway-Ansicht\n"
                    + "Soll: geplante Tunnel-/Stützgeometrie einblenden\n"
                    + "Mausrad: Zoom"
            );
            help.setRows(6);
            inspector.add(help);

            return inspector;
        }

        private void advanceFrame() {
            if (!running) return;
            Speed speed = (Speed) speedSelector.getSelectedItem();
            runtime.runTicks(speed == null ? 1 : speed.ticksPerFrame);
            refresh();
        }

        private void reset() {
            runtime = selectedScenario.createRuntime();
            SimulationRuntime.WorldSnapshot snapshot = runtime.worldSnapshot();
            selectedResidentId = snapshot.mine() == null
                ? null
                : snapshot.residents().stream()
                    .filter(resident -> resident.profession() == Profession.MINER)
                    .map(SimulationRuntime.ResidentSnapshot::id)
                    .findFirst()
                    .orElse(null);
            if (snapshot.mine() != null) {
                selectedLayerY = snapshot.mine().world().bounds().minY();
                viewSelector.setSelectedItem(ViewMode.ISOMETRIC);
            } else {
                viewSelector.setSelectedItem(ViewMode.TOP_DOWN);
            }
            canvas.setRuntime(runtime);
            canvas.setSelectedResidentId(selectedResidentId);
            canvas.setLayerY(selectedLayerY);
            running = false;
            playPauseButton.setText("Start");
            refresh();
        }

        private void changeLayer(int delta) {
            SimulationRuntime.MineSnapshot mine = runtime.worldSnapshot().mine();
            if (mine == null) return;
            MineSimulationWorld.Bounds bounds = mine.world().bounds();
            selectedLayerY = Math.max(
                bounds.minY(),
                Math.min(bounds.maxY(), selectedLayerY + delta)
            );
            canvas.setLayerY(selectedLayerY);
            refresh();
        }

        private void selectResident(String residentId) {
            selectedResidentId = residentId;
            canvas.setSelectedResidentId(residentId);
            refresh();
        }

        private void orderManualMove(WorldPosition destination) {
            if (selectedResidentId == null) return;
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

            boolean mineAvailable = snapshot.mine() != null;
            viewSelector.setEnabled(mineAvailable);
            expectedOverlay.setEnabled(mineAvailable);
            layerDownButton.setEnabled(mineAvailable);
            layerUpButton.setEnabled(mineAvailable);
            if (mineAvailable) {
                MineSimulationWorld.Bounds bounds = snapshot.mine().world().bounds();
                if (selectedLayerY < bounds.minY() || selectedLayerY > bounds.maxY()) {
                    selectedLayerY = bounds.minY();
                    canvas.setLayerY(selectedLayerY);
                }
                layerLabel.setText("Y " + selectedLayerY);
            } else {
                layerLabel.setText("Y –");
            }

            updateResidentDetails(snapshot);
            updateMineDetails(snapshot.mine());
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

        private void updateMineDetails(SimulationRuntime.MineSnapshot mine) {
            if (mine == null) {
                mineDetails.setText("Kein Mine-Szenario aktiv.");
                return;
            }
            int faceSize = MineTuning.TUNNEL_WIDTH_BLOCKS * MineTuning.TUNNEL_HEIGHT_BLOCKS;
            int blockInFace = mine.nextBlockIndex() >= MineTuning.blocksPerSegment()
                ? faceSize
                : mine.nextBlockIndex() % faceSize + 1;
            mineDetails.setText(
                "State: " + mine.state() + "\n"
                    + "Intent: " + mine.intent() + "\n"
                    + "Richtung: " + mine.segment().direction() + "\n"
                    + "Tiefe: " + Math.min(MineTuning.SEGMENT_LENGTH_BLOCKS, mine.currentDepth() + 1)
                    + " / " + MineTuning.SEGMENT_LENGTH_BLOCKS + "\n"
                    + "Block in Fläche: " + blockInFace + " / " + faceSize + "\n"
                    + "Gesamtfortschritt: " + mine.nextBlockIndex() + " / "
                    + MineTuning.blocksPerSegment() + "\n"
                    + "Stützen bestätigt: " + mine.segment().supportsPlaced() + " / 2\n"
                    + "Zielblock: " + (mine.targetBlock() == null ? "–" : mine.targetBlock()) + "\n"
                    + String.format(
                        Locale.ROOT,
                        "Arbeitszeit Block: %.2f / %.2f s",
                        mine.blockWorkElapsedSeconds(),
                        MineTuning.secondsPerBlock()
                    )
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
                    + "Trees felled           " + metrics.treesFelled() + "\n"
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
        private final Color fieldColor = new Color(205, 180, 70);
        private final Color selectedColor = new Color(205, 45, 45);
        private final Color solidColor = new Color(105, 108, 115);
        private final Color airColor = new Color(244, 246, 248);
        private final Color supportPostColor = new Color(128, 84, 48);
        private final Color supportBeamColor = new Color(158, 103, 54);
        private final Color expectedColor = new Color(65, 135, 210, 115);
        private final Color targetColor = new Color(215, 55, 55);

        private SimulationRuntime runtime;
        private SimulationRuntime.WorldSnapshot snapshot;
        private String selectedResidentId;
        private ResidentSelectionListener selectionListener = residentId -> {
        };
        private ManualMoveListener manualMoveListener = destination -> {
        };
        private ViewMode viewMode = ViewMode.TOP_DOWN;
        private int layerY;
        private boolean showExpected = true;
        private double zoom = 1.0;

        private SimulationCanvas() {
            setBackground(Color.WHITE);
            setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (snapshot == null) return;
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        String residentId = residentAt(event.getX(), event.getY());
                        selectionListener.onSelected(residentId);
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(event)
                        && selectedResidentId != null
                        && snapshot.mine() == null) {
                        SimulationRuntime.ResidentSnapshot resident = snapshot.residents().stream()
                            .filter(candidate -> candidate.id().equals(selectedResidentId))
                            .findFirst()
                            .orElse(null);
                        if (resident == null) return;
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

        void setViewMode(ViewMode viewMode) {
            this.viewMode = viewMode;
            repaint();
        }

        void setLayerY(int layerY) {
            this.layerY = layerY;
            repaint();
        }

        void setShowExpected(boolean showExpected) {
            this.showExpected = showExpected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (snapshot == null) return;

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                );
                if (snapshot.mine() != null) {
                    switch (viewMode) {
                        case TOP_DOWN -> drawMineTopDown(g, snapshot.mine(), false);
                        case LAYER -> drawMineTopDown(g, snapshot.mine(), true);
                        case ISOMETRIC -> drawMineIsometric(g, snapshot.mine());
                    }
                    drawMineLegend(g);
                } else {
                    drawGrid(g);
                    drawFields(g);
                    drawConstructionSites(g);
                    drawTrees(g);
                    drawResidents(g);
                    drawLegend(g);
                }
            } finally {
                g.dispose();
            }
        }

        private void drawMineTopDown(
            Graphics2D g,
            SimulationRuntime.MineSnapshot mine,
            boolean singleLayer
        ) {
            MineSimulationWorld.Bounds bounds = mine.world().bounds();
            double cell = mineCellSize(bounds);
            double width = (bounds.maxX() - bounds.minX() + 1) * cell;
            double depth = (bounds.maxZ() - bounds.minZ() + 1) * cell;
            double originX = (getWidth() - width) / 2.0;
            double originY = (getHeight() - depth) / 2.0;
            int y = singleLayer ? layerY : bounds.minY();

            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockPosition position = new BlockPosition(x, y, z);
                    MineSimulationWorld.Cell state = mine.world().get(position);
                    int sx = (int) Math.round(originX + (x - bounds.minX()) * cell);
                    int sy = (int) Math.round(originY + (z - bounds.minZ()) * cell);
                    int size = Math.max(2, (int) Math.ceil(cell));
                    g.setColor(cellColor(state));
                    g.fillRect(sx, sy, size, size);
                    g.setColor(new Color(210, 210, 210));
                    g.drawRect(sx, sy, size, size);

                    if (showExpected) drawExpectedCell(g, mine, position, sx, sy, size);
                    if (position.equals(mine.targetBlock())) {
                        g.setColor(targetColor);
                        g.setStroke(new BasicStroke(3.0f));
                        g.drawRect(sx + 2, sy + 2, Math.max(1, size - 4), Math.max(1, size - 4));
                    }
                }
            }
            drawMineResidentTopDown(g, mine, bounds, originX, originY, cell);
            g.setColor(Color.DARK_GRAY);
            g.drawString(singleLayer ? "Layer Y=" + y : "Draufsicht auf Tunnelboden", 12, 22);
        }

        private void drawExpectedCell(
            Graphics2D g,
            SimulationRuntime.MineSnapshot mine,
            BlockPosition position,
            int sx,
            int sy,
            int size
        ) {
            g.setColor(expectedColor);
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(sx + 3, sy + 3, Math.max(1, size - 6), Math.max(1, size - 6));
            for (int depth : List.of(4, 8)) {
                for (MineSupportFrame.Cell support : MineSupportFrame.cells(mine.segment(), depth)) {
                    if (support.position().equals(position)) {
                        g.setColor(support.part() == MineSupportFrame.Part.POST
                            ? supportPostColor
                            : supportBeamColor);
                        g.setStroke(new BasicStroke(2.2f));
                        g.drawRect(sx + 5, sy + 5, Math.max(1, size - 10), Math.max(1, size - 10));
                        return;
                    }
                }
            }
        }

        private void drawMineResidentTopDown(
            Graphics2D g,
            SimulationRuntime.MineSnapshot mine,
            MineSimulationWorld.Bounds bounds,
            double originX,
            double originY,
            double cell
        ) {
            SimulationRuntime.ResidentSnapshot miner = snapshot.residents().stream()
                .filter(resident -> resident.profession() == Profession.MINER)
                .findFirst()
                .orElse(null);
            if (miner == null) return;
            int x = (int) Math.round(originX + (miner.position().x() - bounds.minX()) * cell);
            int y = (int) Math.round(originY + (miner.position().z() - bounds.minZ()) * cell);
            g.setColor(selectedColor);
            g.fillOval(x - 7, y - 7, 14, 14);
            g.setColor(Color.BLACK);
            g.drawOval(x - 7, y - 7, 14, 14);
            g.drawString("M", x + 9, y + 4);
        }

        private void drawMineIsometric(Graphics2D g, SimulationRuntime.MineSnapshot mine) {
            MineSimulationWorld.Bounds bounds = mine.world().bounds();
            double tileW = 38.0 * zoom;
            double tileH = 19.0 * zoom;
            double cubeH = 20.0 * zoom;
            double centerX = getWidth() * 0.5;
            double baseY = getHeight() * 0.68;

            List<BlockPosition> positions = new ArrayList<>(mine.world().cells().keySet());
            positions.sort(Comparator
                .comparingInt((BlockPosition p) -> (p.x() - bounds.minX()) + (p.z() - bounds.minZ()))
                .thenComparingInt(BlockPosition::y));

            for (BlockPosition position : positions) {
                MineSimulationWorld.Cell cell = mine.world().get(position);
                if (cell == MineSimulationWorld.Cell.AIR) continue;
                double rx = position.x() - bounds.minX();
                double rz = position.z() - bounds.minZ();
                double ry = position.y() - bounds.minY();
                int px = (int) Math.round(centerX + (rx - rz) * tileW / 2.0);
                int py = (int) Math.round(baseY + (rx + rz) * tileH / 2.0 - ry * cubeH);
                drawIsoCube(g, px, py, tileW, tileH, cubeH, cellColor(cell));
            }

            if (showExpected) drawExpectedSupportsIso(g, mine, bounds, centerX, baseY, tileW, tileH, cubeH);
            drawMinerIso(g, bounds, centerX, baseY, tileW, tileH, cubeH);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Isometrische Cutaway-Ansicht – AIR wird nicht gezeichnet", 12, 22);
        }

        private void drawExpectedSupportsIso(
            Graphics2D g,
            SimulationRuntime.MineSnapshot mine,
            MineSimulationWorld.Bounds bounds,
            double centerX,
            double baseY,
            double tileW,
            double tileH,
            double cubeH
        ) {
            for (int depth : List.of(4, 8)) {
                for (MineSupportFrame.Cell support : MineSupportFrame.cells(mine.segment(), depth)) {
                    MineSimulationWorld.Cell actual = mine.world().get(support.position());
                    if (actual == MineSimulationWorld.Cell.SUPPORT_POST
                        || actual == MineSimulationWorld.Cell.SUPPORT_BEAM) continue;
                    BlockPosition p = support.position();
                    double rx = p.x() - bounds.minX();
                    double rz = p.z() - bounds.minZ();
                    double ry = p.y() - bounds.minY();
                    int px = (int) Math.round(centerX + (rx - rz) * tileW / 2.0);
                    int py = (int) Math.round(baseY + (rx + rz) * tileH / 2.0 - ry * cubeH);
                    Polygon top = isoTop(px, py, tileW, tileH);
                    g.setColor(expectedColor);
                    g.setStroke(new BasicStroke(2.0f));
                    g.drawPolygon(top);
                }
            }
        }

        private void drawMinerIso(
            Graphics2D g,
            MineSimulationWorld.Bounds bounds,
            double centerX,
            double baseY,
            double tileW,
            double tileH,
            double cubeH
        ) {
            SimulationRuntime.ResidentSnapshot miner = snapshot.residents().stream()
                .filter(resident -> resident.profession() == Profession.MINER)
                .findFirst()
                .orElse(null);
            if (miner == null) return;
            double rx = miner.position().x() - bounds.minX();
            double rz = miner.position().z() - bounds.minZ();
            double ry = miner.position().y() - bounds.minY();
            int px = (int) Math.round(centerX + (rx - rz) * tileW / 2.0);
            int py = (int) Math.round(baseY + (rx + rz) * tileH / 2.0 - ry * cubeH - cubeH);
            g.setColor(selectedColor);
            g.fillOval(px - 8, py - 8, 16, 16);
            g.setColor(Color.BLACK);
            g.drawOval(px - 8, py - 8, 16, 16);
            g.drawString("M", px + 10, py + 4);
        }

        private void drawIsoCube(
            Graphics2D g,
            int px,
            int py,
            double tileW,
            double tileH,
            double cubeH,
            Color base
        ) {
            Polygon top = isoTop(px, py, tileW, tileH);
            Polygon left = new Polygon(
                new int[]{top.xpoints[3], top.xpoints[2], top.xpoints[2], top.xpoints[3]},
                new int[]{top.ypoints[3], top.ypoints[2], (int) (top.ypoints[2] + cubeH), (int) (top.ypoints[3] + cubeH)},
                4
            );
            Polygon right = new Polygon(
                new int[]{top.xpoints[1], top.xpoints[2], top.xpoints[2], top.xpoints[1]},
                new int[]{top.ypoints[1], top.ypoints[2], (int) (top.ypoints[2] + cubeH), (int) (top.ypoints[1] + cubeH)},
                4
            );
            g.setColor(base.brighter());
            g.fillPolygon(top);
            g.setColor(base.darker());
            g.fillPolygon(left);
            g.setColor(base);
            g.fillPolygon(right);
            g.setColor(new Color(55, 55, 55, 150));
            g.drawPolygon(top);
            g.drawPolygon(left);
            g.drawPolygon(right);
        }

        private static Polygon isoTop(int px, int py, double tileW, double tileH) {
            int halfW = (int) Math.round(tileW / 2.0);
            int halfH = (int) Math.round(tileH / 2.0);
            return new Polygon(
                new int[]{px, px + halfW, px, px - halfW},
                new int[]{py - halfH, py, py + halfH, py},
                4
            );
        }

        private Color cellColor(MineSimulationWorld.Cell cell) {
            return switch (cell) {
                case SOLID -> solidColor;
                case AIR -> airColor;
                case SUPPORT_POST -> supportPostColor;
                case SUPPORT_BEAM -> supportBeamColor;
            };
        }

        private double mineCellSize(MineSimulationWorld.Bounds bounds) {
            double width = bounds.maxX() - bounds.minX() + 1.0;
            double depth = bounds.maxZ() - bounds.minZ() + 1.0;
            return Math.max(
                12.0,
                Math.min(62.0, Math.min((getWidth() - 100.0) / width, (getHeight() - 120.0) / depth))
            ) * zoom;
        }

        private void drawMineLegend(Graphics2D g) {
            String text = "Grau = Stein   Weiß = Luft   Braun = Stütze   Rot = aktueller Zielblock   Blau = Soll";
            int width = g.getFontMetrics().stringWidth(text) + 16;
            int y = getHeight() - 14;
            g.setColor(new Color(255, 255, 255, 225));
            g.fillRect(8, y - 16, width, 22);
            g.setColor(Color.DARK_GRAY);
            g.drawString(text, 16, y);
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
                "W = Holzfäller   B = Bauarbeiter   Fm = Farmer   M = Minenabbauer   T = Baum   B-Quadrat = Baustelle";
            int width = g.getFontMetrics().stringWidth(text) + 16;
            int y = getHeight() - 14;
            g.setColor(new Color(255, 255, 255, 220));
            g.fillRect(8, y - 16, width, 22);
            g.setColor(Color.DARK_GRAY);
            g.drawString(text, 16, y);
        }

        private String residentAt(int mouseX, int mouseY) {
            if (snapshot.mine() != null) {
                return snapshot.residents().stream()
                    .filter(resident -> resident.profession() == Profession.MINER)
                    .map(SimulationRuntime.ResidentSnapshot::id)
                    .findFirst()
                    .orElse(null);
            }
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
                case MINER -> new Color(100, 100, 110);
                case UNEMPLOYED -> Color.GRAY;
            };
        }

        private static String shortProfession(Profession profession) {
            return switch (profession) {
                case WOODCUTTER -> "W";
                case CONSTRUCTION_WORKER -> "B";
                case FARMER -> "Fm";
                case MINER -> "M";
                case UNEMPLOYED -> "–";
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
