import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class SimulacionBoxCulvert extends JPanel implements ActionListener {

    // ── Timer ────────────────────────────────────────────────────────────────
    private final Timer timer;
    private double simTime = 0;
    private boolean paused = false;
    private double wavePhase = 0;

    // ── Parámetros hidráulicos (editables en tiempo real) ────────────────────
    private double B     = 3.0;    // ancho canal (m)
    private double y     = 1.5;    // tirante hidráulico (m)
    private double L     = 10.0;   // largo culvert (m)
    private double n     = 0.015;  // coeficiente Manning
    private double S     = 0.001;  // pendiente longitudinal
    private double turb  = 0.5;    // factor turbulencia 0–1
    private final double GAMMA = 9800;
    private final double G     = 9.81;
    private final double Y_MAX = 3.0;  // altura total del canal (m)

    // ── Resultados calculados ────────────────────────────────────────────────
    private double area, perimetroMojado, radioHidraulico;
    private double velocidadMedia, caudal, fuerzaHidrostatica;
    private double yCentroPresion, volumen, froude, energiaEspecifica;
    private double caudalEfectivo, velocidadEfectiva;
    private String regimen = "—";

    // ── Debris ───────────────────────────────────────────────────────────────
    private final ArrayList<Basura> basura = new ArrayList<>();
    private int basuraRecolectada = 0;
    private int basuraTotal = 0;
    private final Random rnd = new Random();

    // ── Partículas ───────────────────────────────────────────────────────────
    private final ArrayList<Particula> particulas = new ArrayList<>();

    // ── Historial de caudal (gráfica) ────────────────────────────────────────
    private final ArrayList<Double> historialQ = new ArrayList<>();
    private static final int MAX_HIST = 120;

    // ── Toggles visualización ────────────────────────────────────────────────
    private boolean showVectors  = true;
    private boolean showPressure = true;
    private boolean showParticles = false;
    private boolean showGrid     = true;

    // ── Geometría canvas ─────────────────────────────────────────────────────
    private static final int CW = 780, CH = 430;
    private static final int CL = 70, CR = 730, CT = 55, CB = 360;
    private static final int CWIDTH  = CR - CL;   // 660
    private static final int CHEIGHT = CB - CT;   // 305

    // ── Paleta ───────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(7,  8,  15);
    private static final Color BG2      = new Color(13, 16, 32);
    private static final Color SURFACE  = new Color(26, 30, 53);
    private static final Color BORDER   = new Color(99, 120, 255, 60);
    private static final Color ACCENT   = new Color(99, 120, 255);
    private static final Color ACCENT2  = new Color(167, 139, 250);
    private static final Color WATER_T  = new Color(56, 189, 248, 190);
    private static final Color WATER_B  = new Color(26, 79, 214, 220);
    private static final Color OK       = new Color(52, 211, 153);
    private static final Color WARN     = new Color(251, 146, 60);
    private static final Color DANGER   = new Color(248, 113, 113);
    private static final Color MUTED    = new Color(148, 163, 184);
    private static final Color DIMMED   = new Color(74, 85, 104);

    // ── Fuentes ───────────────────────────────────────────────────────────────
    private static final Font FONT_MONO   = new Font("Monospaced", Font.PLAIN,  11);
    private static final Font FONT_MONO_B = new Font("Monospaced", Font.BOLD,   12);
    private static final Font FONT_LABEL  = new Font("SansSerif",  Font.PLAIN,  11);
    private static final Font FONT_TITLE  = new Font("SansSerif",  Font.BOLD,   13);
    private static final Font FONT_BIG    = new Font("Monospaced", Font.BOLD,   18);

    // ═════════════════════════════════════════════════════════════════════════
    public SimulacionBoxCulvert() {
        setPreferredSize(new Dimension(CW, CH));
        setBackground(BG);
        calcular();
        generarBasura(30);
        initParticulas();
        timer = new Timer(20, this);
        timer.start();
    }

    // ─── Cálculo hidráulico completo ──────────────────────────────────────────
    private void calcular() {
        area             = B * y;
        perimetroMojado  = 2 * y + B;
        radioHidraulico  = area / perimetroMojado;
        velocidadMedia   = (1.0 / n) * Math.pow(radioHidraulico, 2.0/3) * Math.sqrt(S);
        caudal           = velocidadMedia * area;
        fuerzaHidrostatica = GAMMA * B * y * y / 2.0;
        yCentroPresion   = (2.0 / 3.0) * y;
        volumen          = B * y * L;
        froude           = velocidadMedia / Math.sqrt(G * y);
        energiaEspecifica = y + (velocidadMedia * velocidadMedia) / (2 * G);

        double bloqueo = basuraTotal > 0 ? (double) basuraRecolectada / basuraTotal : 0;
        double factor  = 1 - bloqueo * 0.55;
        caudalEfectivo    = caudal * factor;
        velocidadEfectiva = velocidadMedia * factor;

        if (froude < 0.9)       regimen = "Subcrítico (Fr<1)";
        else if (froude > 1.1)  regimen = "Supercrítico (Fr>1)";
        else                    regimen = "Crítico (Fr≈1)";
    }

    // ─── Debris ───────────────────────────────────────────────────────────────
    private void generarBasura(int count) {
        basura.clear();
        basuraRecolectada = 0;
        basuraTotal = count;
        for (int i = 0; i < count; i++) basura.add(nuevaBasura());
    }

    private Basura nuevaBasura() {
        double xFrac = 0.05 + rnd.nextDouble() * 0.75;
        double yFrac = 0.05 + rnd.nextDouble() * 0.9;
        return new Basura(xFrac, yFrac, rnd.nextInt(3));
    }

    // ─── Partículas streamline ────────────────────────────────────────────────
    private void initParticulas() {
        particulas.clear();
        for (int i = 0; i < 70; i++) {
            particulas.add(new Particula(rnd.nextDouble(), 0.05 + rnd.nextDouble() * 0.9));
        }
    }

    // ─── Timer / loop ─────────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!paused) {
            simTime  += 0.02;
            wavePhase += 0.05;
            calcular();
            actualizarDebris();
            actualizarParticulas();

            if ((int)(simTime * 50) % 4 == 0) {
                historialQ.add(caudalEfectivo);
                if (historialQ.size() > MAX_HIST) historialQ.remove(0);
            }
        }
        repaint();
    }

    private void actualizarDebris() {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        for (Basura b : basura) {
            if (b.recolectada) continue;

            double profFactor = Math.pow(Math.max(b.yFrac, 0.01), 0.2);
            double vLocal = velocidadMedia * profFactor;
            double turbVal = (rnd.nextDouble() - 0.5) * turb * 0.5;
            b.xFrac += (vLocal * 0.0004) + turbVal * 0.001;

            b.vyFrac += (rnd.nextDouble() - 0.5) * turb * 0.004;
            b.vyFrac *= 0.96;
            b.yFrac  += b.vyFrac;

            if (b.yFrac < 0.03) { b.yFrac = 0.03; b.vyFrac =  Math.abs(b.vyFrac) + rnd.nextDouble() * 0.002; }
            if (b.yFrac > 0.97) { b.yFrac = 0.97; b.vyFrac = -Math.abs(b.vyFrac) - rnd.nextDouble() * 0.002; }

            // Recolección en rejilla
            if (b.xFrac >= 0.88 && b.xFrac <= 0.95) {
                b.recolectada = true;
                basuraRecolectada++;
            }
            // Escapó
            if (b.xFrac > 1.0) b.recolectada = true;
        }
    }

    private void actualizarParticulas() {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        for (Particula p : particulas) {
            double profFactor = Math.pow(Math.max(p.yFrac / Math.max(waterFrac, 0.01), 0.01), 0.15);
            double vLocal = Math.max(velocidadMedia * profFactor, 0.01);
            p.xFrac += vLocal * 0.0007;
            p.age++;
            if (p.xFrac > 1.0 || p.age > p.maxAge) {
                p.xFrac = 0;
                p.yFrac = 0.05 + rnd.nextDouble() * 0.9 * waterFrac;
                p.age = 0;
                p.maxAge = 120 + rnd.nextInt(100);
            }
        }
    }

    // ─── Pintado ──────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);

        // Fondo
        g2.setColor(BG);
        g2.fillRect(0, 0, CW, CH);

        dibujarCanal(g2);
        if (showGrid)     dibujarGrid(g2);
        dibujarAgua(g2);
        if (showPressure) dibujarPresiones(g2);
        if (showParticles) dibujarParticulas(g2);
        if (showVectors)  dibujarVectores(g2);
        dibujarDebris(g2);
        dibujarRejilla(g2);
        dibujarCotas(g2);
        dibujarHUD(g2);
        dibujarGrafica(g2);
    }

    // ── Canal ─────────────────────────────────────────────────────────────────
    private void dibujarCanal(Graphics2D g2) {
        // Sombra exterior (glow)
        for (int i = 8; i > 0; i--) {
            g2.setColor(new Color(99, 120, 255, i * 3));
            g2.setStroke(new BasicStroke(i * 2));
            g2.drawRoundRect(CL - 18 - i, CT - 18 - i, CWIDTH + 36 + i*2, CHEIGHT + 18 + 40 + i*2, 16, 16);
        }
        // Cuerpo canal (concreto)
        g2.setColor(SURFACE);
        g2.fillRoundRect(CL - 18, CT - 18, CWIDTH + 36, CHEIGHT + 18 + 40, 16, 16);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(99, 120, 255, 100));
        g2.drawRoundRect(CL - 18, CT - 18, CWIDTH + 36, CHEIGHT + 18 + 40, 16, 16);

        // Líneas textura concreto
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(99, 120, 255, 20));
        for (int i = 0; i < 5; i++) {
            g2.drawLine(CL - 18 + i*4, CT - 18, CL - 18 + i*4, CB + 22);
            g2.drawLine(CR + 18 - i*4, CT - 18, CR + 18 - i*4, CB + 22);
        }
    }

    // ── Grid ─────────────────────────────────────────────────────────────────
    private void dibujarGrid(Graphics2D g2) {
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(99, 120, 255, 20));
        for (int x = CL; x <= CR; x += 44) {
            g2.drawLine(x, CT - 10, x, CB + 10);
        }
        for (int i = 0; i <= 4; i++) {
            int gy = CT + i * CHEIGHT / 4;
            g2.drawLine(CL, gy, CR, gy);
        }
    }

    // ── Agua ─────────────────────────────────────────────────────────────────
    private void dibujarAgua(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        int waterTop = CB - (int)(waterFrac * CHEIGHT);
        int waterBot = CB;

        // Gradiente de agua
        GradientPaint grad = new GradientPaint(CL, waterTop, WATER_T, CL, waterBot, WATER_B);
        g2.setPaint(grad);

        // Forma con superficie ondulada
        GeneralPath waterShape = new GeneralPath();
        waterShape.moveTo(CL, waterBot);
        waterShape.lineTo(CL, waterTop);
        double amp = 2.0 + froude * 1.5;
        for (int px = CL; px <= CR; px += 2) {
            double wy = waterTop
                    + Math.sin((px + wavePhase * 15) / 60.0 * Math.PI * 2) * amp
                    + Math.sin((px + wavePhase * 8)  / 35.0 * Math.PI * 2) * (amp * 0.4);
            waterShape.lineTo(px, wy);
        }
        waterShape.lineTo(CR, waterBot);
        waterShape.closePath();
        g2.fill(waterShape);

        // Brillo en la superficie
        GradientPaint shine = new GradientPaint(CL, waterTop - 2, new Color(186, 230, 253, 90), CL, waterTop + 28, new Color(186, 230, 253, 0));
        g2.setPaint(shine);
        g2.fillRect(CL, waterTop - 2, CWIDTH, 30);
    }

    // ── Diagrama de presiones ─────────────────────────────────────────────────
    private void dibujarPresiones(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        int waterTop = CB - (int)(waterFrac * CHEIGHT);
        int waterBot = CB;
        int presW = 48;

        GeneralPath presShape = new GeneralPath();
        presShape.moveTo(CL - 8, waterBot);
        presShape.lineTo(CL - 8, waterTop);
        int steps = 24;
        for (int j = 0; j <= steps; j++) {
            double frac = (double) j / steps;
            double depth = (1 - frac) * y;
            double p = GAMMA * depth;
            double pNorm = Math.min(p / (GAMMA * Y_MAX), 1.0);
            int px = (int)(CL - 8 - pNorm * presW);
            int py = waterTop + (int)(frac * (waterBot - waterTop));
            presShape.lineTo(px, py);
        }
        presShape.closePath();
        GradientPaint pGrad = new GradientPaint(CL - 8 - presW, 0, new Color(167, 139, 250, 130), CL - 8, 0, new Color(167, 139, 250, 15));
        g2.setPaint(pGrad);
        g2.fill(presShape);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(167, 139, 250, 180));
        g2.draw(presShape);

        // Etiqueta p(z)
        g2.setFont(FONT_MONO);
        g2.setColor(ACCENT2);
        g2.drawString("p(z)", CL - 8 - presW / 2 - 10, waterBot + 14);

        // Marcador centro de presión
        double cpFrac = (yCentroPresion / Y_MAX);
        int cpY = CB - (int)(cpFrac * CHEIGHT);
        g2.setColor(ACCENT2);
        g2.fillOval(CL - 12, cpY - 4, 8, 8);
        g2.setFont(FONT_MONO);
        g2.setColor(ACCENT2);
        g2.drawString("ycp", CL - 38, cpY + 4);
    }

    // ── Partículas streamline ─────────────────────────────────────────────────
    private void dibujarParticulas(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        for (Particula p : particulas) {
            if (p.yFrac > waterFrac) continue;
            int px = CL + (int)(p.xFrac * CWIDTH);
            int py = CB - (int)(p.yFrac * CHEIGHT);
            double t1 = Math.min((double) p.age / 20, 1.0);
            double t2 = Math.min((double)(p.maxAge - p.age) / 20, 1.0);
            int alpha = (int)(Math.min(t1, t2) * 160);
            if (alpha < 10) continue;
            g2.setColor(new Color(186, 230, 253, alpha));
            g2.fillOval(px - 2, py - 2, 4, 4);
        }
    }

    // ── Vectores de velocidad ─────────────────────────────────────────────────
    private void dibujarVectores(Graphics2D g2) {
        if (velocidadMedia <= 0) return;
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        int rows = 5, cols = 8;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double fyFrac = (0.05 + (double) r / (rows - 1) * 0.9) * waterFrac;
                double fxFrac = (c + 0.5) / cols;
                double profFactor = Math.pow(fyFrac / Math.max(waterFrac, 0.01), 0.2);
                double vLocal = velocidadEfectiva * profFactor;
                int len = (int) Math.min(vLocal / 4.0, 1.0) * 28 + 8;
                len = Math.min(len, 36);
                int vx = CL + (int)(fxFrac * CWIDTH);
                int vy = CB - (int)(fyFrac * CHEIGHT);

                // Color por velocidad: azul → cian → blanco
                float tV = (float) Math.min(vLocal / 3.0, 1.0);
                int rC = (int)(56  + tV * 200);
                int gC = (int)(189 - tV * 50);
                int bC = (int)(248 - tV * 50);
                g2.setColor(new Color(rC, gC, bC, 180));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(vx, vy, vx + len, vy);
                // Flecha
                int[] ax = { vx + len, vx + len - 7, vx + len - 7 };
                int[] ay = { vy, vy - 4, vy + 4 };
                g2.fillPolygon(ax, ay, 3);
            }
        }
    }

    // ── Debris ────────────────────────────────────────────────────────────────
    private void dibujarDebris(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        for (Basura b : basura) {
            if (b.recolectada) continue;
            int px = CL + (int)(b.xFrac * CWIDTH);
            int py = CB - (int)(b.yFrac * waterFrac * CHEIGHT);

            g2.setColor(b.color);
            g2.setStroke(new BasicStroke(1f));
            switch (b.shape) {
                case 0 -> { g2.fillOval(px - 5, py - 5, 10, 10); g2.setColor(b.color.darker()); g2.drawOval(px - 5, py - 5, 10, 10); }
                case 1 -> { g2.fillRect(px - 4, py - 4, 8, 8);  g2.setColor(b.color.darker()); g2.drawRect(px - 4, py - 4, 8, 8); }
                case 2 -> {
                    int[] tx = { px, px + 6, px - 6 }, ty = { py - 6, py + 5, py + 5 };
                    g2.fillPolygon(tx, ty, 3);
                    g2.setColor(b.color.darker()); g2.drawPolygon(tx, ty, 3);
                }
            }
        }
    }

    // ── Rejilla ───────────────────────────────────────────────────────────────
    private void dibujarRejilla(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        int waterTop = CB - (int)(waterFrac * CHEIGHT);
        int gX = CR - 12;
        g2.setStroke(new BasicStroke(2.5f));
        g2.setColor(ACCENT2);
        int bars = 14;
        for (int i = 0; i <= bars; i++) {
            int gy = waterTop + 4 + i * ((CB - waterTop - 4) / bars);
            g2.drawLine(gX, gy, gX + 14, gy);
        }
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(167, 139, 250, 100));
        g2.drawRect(gX, waterTop, 14, CB - waterTop);
    }

    // ── Cotas dimensionales ────────────────────────────────────────────────────
    private void dibujarCotas(Graphics2D g2) {
        double waterFrac = Math.min(y / Y_MAX, 1.0);
        int waterTop = CB - (int)(waterFrac * CHEIGHT);

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(148, 163, 184, 160));
        g2.setFont(FONT_MONO);

        // Flecha ancho B (abajo)
        int arY = CB + 18;
        g2.drawLine(CL, arY, CR, arY);
        g2.drawLine(CL, arY - 4, CL, arY + 4);
        g2.drawLine(CR, arY - 4, CR, arY + 4);
        String bLabel = String.format("B = %.2f m", B);
        g2.setColor(MUTED);
        g2.drawString(bLabel, (CL + CR) / 2 - 30, arY + 12);

        // Flecha tirante y (izquierda)
        int arX = CL - 40;
        g2.setColor(new Color(148, 163, 184, 160));
        g2.drawLine(arX, waterTop, arX, CB);
        g2.drawLine(arX - 4, waterTop, arX + 4, waterTop);
        g2.drawLine(arX - 4, CB, arX + 4, CB);
        Graphics2D g3 = (Graphics2D) g2.create();
        g3.setFont(FONT_MONO);
        g3.setColor(MUTED);
        g3.translate(arX - 12, (waterTop + CB) / 2);
        g3.rotate(-Math.PI / 2);
        g3.drawString(String.format("y=%.2fm", y), -22, 0);
        g3.dispose();

        // Froude label
        Color frColor = froude < 0.9 ? new Color(56,189,248) : froude > 1.1 ? DANGER : WARN;
        g2.setFont(FONT_MONO_B);
        g2.setColor(frColor);
        g2.drawString(String.format("Fr=%.3f", froude), CL + 8, CT + 16);

        // Flecha de flujo Q
        g2.setColor(new Color(56, 189, 248, 120));
        int midY = (waterTop + CB) / 2;
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(CL + 18, midY, CL + 80, midY);
        int[] qax = { CL + 80, CL + 73, CL + 73 };
        int[] qay = { midY, midY - 4, midY + 4 };
        g2.fillPolygon(qax, qay, 3);
        g2.setFont(FONT_MONO);
        g2.setColor(new Color(56, 189, 248, 160));
        g2.drawString("Q →", CL + 32, midY - 7);
    }

    // ── HUD panel inferior ─────────────────────────────────────────────────────
    private void dibujarHUD(Graphics2D g2) {

        // Panel HUD
        int hx = 0, hy = CH - 68, hw = CW, hh = 68;
        g2.setColor(new Color(13, 16, 32, 230));
        g2.fillRect(hx, hy, hw, hh);
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(BORDER);
        g2.drawLine(hx, hy, hx + hw, hy);

        // INTEGRALES EN EL CUADRANTE INFERIOR IZQUIERDO
        int integralesX = 18;
        int integralesY = CH - 110;
        g2.setFont(FONT_BIG);
        g2.setColor(ACCENT2);
        g2.drawString("∬ₐ dydx = Área total", integralesX, integralesY);
        g2.setFont(FONT_TITLE);
        g2.setColor(ACCENT);
        g2.drawString("A(y) = ∫₀ʸ B dη = B·y", integralesX, integralesY + 26);
        g2.setColor(MUTED);
        g2.setFont(FONT_LABEL);
        g2.drawString("Q = ∫ₐ v dA", integralesX, integralesY + 46);
        g2.drawString("Fₕ = γ·B·y²/2", integralesX, integralesY + 66);

        // Estado
        double bloqueo = basuraTotal > 0 ? (double) basuraRecolectada / basuraTotal : 0;
        Color stateColor;
        String stateLabel;
        if (bloqueo > 0.6 || froude > 1.1) { stateColor = DANGER; stateLabel = "PELIGRO"; }
        else if (bloqueo > 0.3 || froude > 0.85) { stateColor = WARN; stateLabel = "PRECAUCION"; }
        else { stateColor = OK; stateLabel = "NORMAL"; }

        g2.setColor(stateColor);
        g2.fillOval(12, hy + 8, 10, 10);
        g2.setFont(FONT_TITLE);
        g2.drawString(stateLabel, 28, hy + 18);

        // Tiempo
        g2.setFont(FONT_MONO);
        g2.setColor(DIMMED);
        g2.drawString(String.format("t=%.1fs", simTime), 28, hy + 34);

        // Métricas en línea
        int mx = 140, my = hy + 14, sep = 120;
        String[] labels = { "Q efectivo", "Velocidad", "Radio Hid.", "F.Hidrost.", "Energía E", "Froude" };
        String[] vals   = {
            String.format("%.3f m³/s", caudalEfectivo),
            String.format("%.3f m/s",  velocidadEfectiva),
            String.format("%.3f m",    radioHidraulico),
            String.format("%.0f N/m",  fuerzaHidrostatica),
            String.format("%.3f m",    energiaEspecifica),
            String.format("%.3f",      froude)
        };
        for (int i = 0; i < labels.length; i++) {
            int x = mx + i * sep;
            g2.setFont(FONT_MONO);
            g2.setColor(MUTED);
            g2.drawString(labels[i], x, my);
            g2.setFont(FONT_MONO_B);
            g2.setColor(i == 5 ? stateColor : new Color(226, 232, 240));
            g2.drawString(vals[i], x, my + 17);
        }

        // Basura / bloqueo
        g2.setFont(FONT_MONO);
        g2.setColor(MUTED);
        g2.drawString("Basura:", mx, hy + 50);
        g2.setColor(Color.WHITE);
        g2.drawString(basuraRecolectada + "/" + basuraTotal, mx + 52, hy + 50);

        int barX = mx + 110, barY = hy + 42, barW = 160, barH = 8;
        g2.setColor(SURFACE);
        g2.fillRoundRect(barX, barY, barW, barH, 4, 4);
        Color barColor = bloqueo > 0.6 ? DANGER : bloqueo > 0.3 ? WARN : OK;
        g2.setColor(barColor);
        g2.fillRoundRect(barX, barY, (int)(barW * bloqueo), barH, 4, 4);
        g2.setFont(FONT_MONO);
        g2.setColor(MUTED);
        g2.drawString(String.format("Bloqueo %.0f%%", bloqueo * 100), barX + barW + 6, barY + 8);

        // Régimen
        g2.setFont(FONT_MONO);
        g2.setColor(stateColor);
        g2.drawString(regimen, barX, hy + 62);
    }

    // ── Gráfica mini de caudal ─────────────────────────────────────────────────
    private void dibujarGrafica(Graphics2D g2) {
        if (historialQ.size() < 2) return;
        int gx = CW - 170, gy = CT + 5, gw = 155, gh = 50;

        g2.setColor(new Color(13, 16, 32, 200));
        g2.fillRoundRect(gx, gy, gw, gh, 8, 8);
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(BORDER);
        g2.drawRoundRect(gx, gy, gw, gh, 8, 8);

        g2.setFont(FONT_MONO);
        g2.setColor(DIMMED);
        g2.drawString("Q(t)", gx + 4, gy + 12);

        double maxQ = historialQ.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double minQ = 0;

        g2.setStroke(new BasicStroke(1.5f));
        GeneralPath path = new GeneralPath();
        for (int i = 0; i < historialQ.size(); i++) {
            double qv = historialQ.get(i);
            float px = gx + 4 + (float) i / (historialQ.size() - 1) * (gw - 8);
            float py = gy + gh - 6 - (float)((qv - minQ) / Math.max(maxQ - minQ, 0.001)) * (gh - 16);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        g2.setColor(ACCENT);
        g2.draw(path);

        // Valor actual
        g2.setFont(FONT_MONO_B);
        g2.setColor(ACCENT2);
        g2.drawString(String.format("%.3f", caudalEfectivo), gx + 80, gy + 12);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Clases internas
    // ══════════════════════════════════════════════════════════════════════════

    static class Basura {
        double xFrac, yFrac, vyFrac;
        int shape;
        Color color;
        boolean recolectada = false;

        Basura(double x, double y, int shape) {
            this.xFrac = x; this.yFrac = y; this.shape = shape;
            this.vyFrac = (Math.random() - 0.5) * 0.006;
            float hue = (float)(0.22 + Math.random() * 0.25);
            this.color = Color.getHSBColor(hue, 0.75f, 0.75f);
        }
    }

    static class Particula {
        double xFrac, yFrac;
        int age, maxAge;
        Particula(double x, double y) {
            this.xFrac = x; this.yFrac = y;
            this.age = (int)(Math.random() * 150);
            this.maxAge = 120 + (int)(Math.random() * 100);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Panel de control lateral
    // ══════════════════════════════════════════════════════════════════════════

    private JPanel crearPanelControl() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(13, 16, 32));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        panel.setPreferredSize(new Dimension(220, 0));

        panel.add(makeTitle("PARÁMETROS GEOMÉTRICOS"));
        panel.add(makeSlider("Ancho B (m)",        0.5, 6.0, B,     v -> { B = v; }));
        panel.add(makeSlider("Tirante y (m)",       0.1, 2.8, y,     v -> { y = v; }));
        panel.add(makeSlider("Largo L (m)",         2.0, 30.0, L,    v -> { L = v; }));
        panel.add(Box.createVerticalStrut(8));

        panel.add(makeTitle("PARÁMETROS HIDRÁULICOS"));
        panel.add(makeSlider("Manning n",           0.008, 0.035, n, v -> { n = v; }));
        panel.add(makeSlider("Pendiente S₀",        0.0001, 0.02, S, v -> { S = v; }));
        panel.add(makeSlider("Turbulencia",         0.0, 1.0, turb,  v -> { turb = v; }));
        panel.add(Box.createVerticalStrut(8));

        panel.add(makeTitle("VISUALIZACIÓN"));
        panel.add(makeToggle("Vectores velocidad",  showVectors,   v -> showVectors  = v));
        panel.add(makeToggle("Diagrama presiones",  showPressure,  v -> showPressure = v));
        panel.add(makeToggle("Streamlines",         showParticles, v -> { showParticles = v; if (v) initParticulas(); }));
        panel.add(makeToggle("Cuadrícula",          showGrid,      v -> showGrid = v));
        panel.add(Box.createVerticalStrut(8));

        panel.add(makeTitle("CONTROLES"));
        panel.add(makeBtn("↺  Reiniciar basura",  new Color(52, 211, 153),  () -> generarBasura(30)));
        panel.add(makeBtn("+ Agregar basura",      new Color(251, 146, 60), () -> {
            for (int i = 0; i < 5; i++) { basura.add(nuevaBasura()); basuraTotal++; }
        }));
        panel.add(makeBtn("⏸  Pausar / Reanudar", new Color(99, 120, 255),  () -> paused = !paused));
        panel.add(Box.createVerticalStrut(8));

        panel.add(makeTitle("ECUACIONES"));
        panel.add(makeEq("A = B·y"));
        panel.add(makeEq("P = 2y + B"));
        panel.add(makeEq("R = A / P"));
        panel.add(makeEq("v = R²/³·S½ / n"));
        panel.add(makeEq("Q = v·A"));
        panel.add(makeEq("F = γ·B·y²/2"));
        panel.add(makeEq("Fr = v/√(g·y)"));
        panel.add(makeEq("E = y + v²/2g"));

        return panel;
    }

    // ── Helpers de construcción de UI ─────────────────────────────────────────

    private JLabel makeTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        l.setForeground(ACCENT);
        l.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel makeEq(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(new Font("Monospaced", Font.PLAIN, 10));
        l.setForeground(new Color(167, 139, 250));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    @FunctionalInterface interface DoubleConsumer { void accept(double v); }
    @FunctionalInterface interface BoolConsumer   { void accept(boolean v); }
    @FunctionalInterface interface Runnable2      { void run(); }

    private JPanel makeSlider(String label, double min, double max, double initial, DoubleConsumer onChange) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setBackground(new Color(13, 16, 32));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(MUTED);

        JLabel val = new JLabel(String.format("%.4f", initial));
        val.setFont(FONT_MONO);
        val.setForeground(ACCENT2);
        val.setHorizontalAlignment(SwingConstants.RIGHT);

        JSlider sl = new JSlider(0, 1000, (int)((initial - min) / (max - min) * 1000));
        sl.setBackground(new Color(13, 16, 32));
        sl.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        sl.addChangeListener(e -> {
            double v = min + sl.getValue() / 1000.0 * (max - min);
            onChange.accept(v);
            val.setText(String.format("%.4f", v));
        });

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(13, 16, 32));
        top.add(lbl, BorderLayout.WEST);
        top.add(val, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);
        p.add(sl,  BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return p;
    }

    private JPanel makeToggle(String label, boolean initial, BoolConsumer onChange) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(new Color(13, 16, 32));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(MUTED);

        JCheckBox cb = new JCheckBox("", initial);
        cb.setBackground(new Color(13, 16, 32));
        cb.addActionListener(e -> onChange.accept(cb.isSelected()));

        p.add(lbl, BorderLayout.WEST);
        p.add(cb,  BorderLayout.EAST);
        return p;
    }

    private JButton makeBtn(String text, Color color, Runnable2 action) {
        JButton b = new JButton(text);
        b.setFont(FONT_LABEL);
        b.setForeground(color);
        b.setBackground(new Color(26, 30, 53));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100), 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.addActionListener(e -> action.run());
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(40, 46, 80)); }
            public void mouseExited(MouseEvent e)  { b.setBackground(new Color(26, 30, 53)); }
        });
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(new Color(13, 16, 32));
        wrap.add(b);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Retornamos el botón directamente en un wrapper JPanel,
        // pero para simplificar lo insertamos en el panel principal
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN
    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Box Culvert — Simulación Hidráulica Avanzada");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            SimulacionBoxCulvert sim = new SimulacionBoxCulvert();
            JScrollPane controlScroll = new JScrollPane(sim.crearPanelControl());
            controlScroll.setBorder(BorderFactory.createLineBorder(new Color(99, 120, 255, 60)));
            controlScroll.getViewport().setBackground(new Color(13, 16, 32));
            controlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlScroll, sim);
            split.setDividerLocation(225);
            split.setDividerSize(1);
            split.setBorder(null);

            frame.setLayout(new BorderLayout());
            frame.add(split, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}