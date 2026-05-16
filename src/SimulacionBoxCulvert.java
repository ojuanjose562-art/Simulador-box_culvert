import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class SimulacionBoxCulvert extends JPanel
        implements ActionListener, KeyListener {

    Timer timer;

    int anchoCanal = 700;
    int altoCanal = 250;

    double velocidadAgua = 2.0;
    double alturaAgua = 100;
    double anchoAgua = 700;

    // Parámetros hidráulicos
    double area;
    double areaHastaY;
    double caudal;
    double velocidadMedia;
    double radioHidraulico;
    double perimetroMojado;
    double fuerzaHidrostatica;
    double yCentroPresion;
    double volumenRes;

    // Manning
    double n = 0.015;
    double S = 0.001;
    double gammaAgua = 9800;
    double largoCanal = 10.0;

    int basuraRecolectada = 0;
    int basuraTotal = 0;

    ArrayList<Basura> basura = new ArrayList<>();

    Random random = new Random();

    int nivelFondo = 330;
    int nivelAgua = 230;

    double faseOndas = 0;

    // NUEVAS VARIABLES
    String estadoSistema = "NORMAL";

    double porcentajeBloqueo = 0;

    double nivelTurbulencia = 0.5;

    boolean modoLluvia = false;

    Color colorNeon = new Color(180, 0, 255);

    Color fondoOscuro = new Color(8, 8, 18);

    Color panelHUD = new Color(20, 20, 35, 220);

    Font fuenteHUD = new Font("Consolas", Font.BOLD, 15);

    public SimulacionBoxCulvert() {

        setPreferredSize(new Dimension(1000, 750));

        setBackground(Color.BLACK);

        setFocusable(true);

        addKeyListener(this);

        calcularArea();

        generarBasura();

        timer = new Timer(20, this);

        timer.start();
    }

    public void calcularArea() {

        double B = anchoAgua * 0.01;

        double H = alturaAgua * 0.01;

        // 1 Área total
        area = B * H;

        // 2 Área parcial
        double y = H;

        areaHastaY = B * y;

        // 3 Perímetro mojado
        perimetroMojado = 2 * y + B;

        // 4 Radio hidráulico
        radioHidraulico = areaHastaY / perimetroMojado;

        // 5 Manning
        velocidadMedia =
                (1.0 / n)
                        * Math.pow(radioHidraulico, 2.0 / 3.0)
                        * Math.pow(S, 0.5);

        // 6 Caudal
        caudal = velocidadMedia * areaHastaY;

        // 7 Fuerza hidrostática
        fuerzaHidrostatica =
                gammaAgua * B * y * y / 2.0;

        // 8 Centro presión
        yCentroPresion = (2.0 / 3.0) * y;

        // 9 Volumen
        volumenRes = B * H * largoCanal;
    }

    public void generarBasura() {

        basura.clear();

        basuraRecolectada = 0;

        basuraTotal = 0;

        for (int i = 0; i < 30; i++) {

            int x = 60 + random.nextInt(450);

            int y = nivelAgua + 10 +
                    random.nextInt((int) alturaAgua - 20);

            double vy =
                    (random.nextDouble() - 0.5) * 0.5;

            basura.add(new Basura(x, y, vy));

            basuraTotal++;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        renderizar((Graphics2D) g);
    }

    private void renderizar(Graphics2D g2) {

        // ANTIALIAS
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        // FONDO FUTURISTA
        GradientPaint fondo =
                new GradientPaint(
                        0,
                        0,
                        new Color(5, 5, 15),

                        0,
                        getHeight(),

                        new Color(40, 0, 70)
                );

        g2.setPaint(fondo);

        g2.fillRect(0, 0, getWidth(), getHeight());

        // EFECTO GLOW
        g2.setColor(new Color(180, 0, 255, 40));

        for (int i = 0; i < 10; i++) {

            g2.drawRoundRect(
                    45 - i,
                    nivelAgua - 55 - i,
                    anchoCanal + 10 + (i * 2),
                    altoCanal + 10 + (i * 2),
                    35,
                    35
            );
        }

        // CANAL
        g2.setColor(new Color(25, 25, 35));

        g2.fillRoundRect(
                50,
                nivelAgua - 50,
                anchoCanal,
                altoCanal,
                30,
                30
        );

        // AGUA NEON
        GradientPaint grad =
                new GradientPaint(
                        50,
                        nivelAgua,
                        new Color(160, 0, 255, 220),

                        50,
                        nivelAgua + (int) alturaAgua,

                        new Color(70, 0, 180, 240)
                );

        g2.setPaint(grad);

        g2.fillRect(
                50,
                nivelAgua,
                anchoCanal,
                (int) alturaAgua
        );

        // ONDAS
        g2.setColor(new Color(255, 255, 255, 90));

        for (int j = 0; j < 3; j++) {

            int amp = 6 - j * 2;

            int freq = 60 + j * 20;

            int yBase = nivelAgua + 20 + j * 20;

            for (int x = 50;
                 x < 50 + anchoCanal;
                 x += 2) {

                double y =
                        yBase +
                                Math.sin(
                                        (x + faseOndas * 10 + j * 30)
                                                / freq
                                                * 2
                                                * Math.PI
                                ) * amp;

                g2.drawLine(
                        x,
                        (int) y,
                        x + 2,
                        (int) y
                );
            }
        }

        // REJILLA
        int rejillaX = 600;

        g2.setStroke(new BasicStroke(3f));

        g2.setColor(new Color(200, 200, 255));

        for (int i = 0; i < 12; i++) {

            int y = nivelAgua + 5 + i * 8;

            g2.drawLine(
                    rejillaX,
                    y,
                    rejillaX + 12,
                    y
            );
        }

        // BASURA
        for (Basura b : basura) {

            if (!b.recolectada) {

                g2.setColor(new Color(0, 255, 120));

                g2.fillOval(
                        (int) b.x,
                        (int) b.y,
                        15,
                        15
                );

                g2.setColor(new Color(0, 80, 0));

                g2.drawOval(
                        (int) b.x,
                        (int) b.y,
                        15,
                        15
                );
            }
        }

        // HUD
        g2.setColor(panelHUD);

        g2.fillRoundRect(
                40,
                390,
                900,
                300,
                30,
                30
        );

        g2.setColor(colorNeon);

        g2.setStroke(new BasicStroke(3f));

        g2.drawRoundRect(
                40,
                390,
                900,
                300,
                30,
                30
        );

        // TITULO
        g2.setColor(Color.WHITE);

        g2.setFont(new Font("Consolas", Font.BOLD, 24));

        g2.drawString(
                "BOX CULVERT AI HYDRO SYSTEM",
                240,
                50
        );

        // LED
        if (estadoSistema.equals("NORMAL")) {

            g2.setColor(Color.GREEN);

        } else if (
                estadoSistema.equals("PRECAUCION")
        ) {

            g2.setColor(Color.ORANGE);

        } else {

            g2.setColor(Color.RED);
        }

        g2.fillOval(820, 30, 20, 20);

        g2.setColor(Color.WHITE);

        g2.drawString(
                estadoSistema,
                850,
                45
        );

        // INFORMACIÓN
        g2.setFont(fuenteHUD);

        int infoX = 70;

        int infoY = 430;

        int sep = 25;

        g2.drawString(
                "Área total: "
                        + String.format("%.3f", area)
                        + " m²",
                infoX,
                infoY
        );

        g2.drawString(
                "A = ∫∫ dydx",
                350,
                infoY
        );

        g2.drawString(
                "Área parcial: "
                        + String.format("%.3f", areaHastaY),
                infoX,
                infoY + sep
        );

        g2.drawString(
                "A(y)=∫ B dη",
                350,
                infoY + sep
        );

        g2.drawString(
                "Perímetro mojado: "
                        + String.format("%.3f",
                        perimetroMojado),
                infoX,
                infoY + sep * 2
        );

        g2.drawString(
                "Radio hidráulico: "
                        + String.format("%.3f",
                        radioHidraulico),
                infoX,
                infoY + sep * 3
        );

        g2.drawString(
                "Velocidad Manning: "
                        + String.format("%.3f",
                        velocidadMedia),
                infoX,
                infoY + sep * 4
        );

        g2.drawString(
                "Caudal: "
                        + String.format("%.3f",
                        caudal)
                        + " m³/s",
                infoX,
                infoY + sep * 5
        );

        g2.drawString(
                "Fuerza Hidrostática: "
                        + String.format("%.1f",
                        fuerzaHidrostatica)
                        + " N",
                infoX,
                infoY + sep * 6
        );

        g2.drawString(
                "Centro de presión: "
                        + String.format("%.3f",
                        yCentroPresion),
                infoX,
                infoY + sep * 7
        );

        g2.drawString(
                "Volumen reserva: "
                        + String.format("%.2f",
                        volumenRes)
                        + " m³",
                infoX,
                infoY + sep * 8
        );

        g2.drawString(
                "Basura recolectada: "
                        + basuraRecolectada
                        + "/"
                        + basuraTotal,
                600,
                430
        );

        // EFICIENCIA
        double eficiencia =
                (double) basuraRecolectada
                        / Math.max(basuraTotal, 1)
                        * 100;

        g2.drawString(
                "Eficiencia: "
                        + String.format("%.1f",
                        eficiencia)
                        + "%",
                600,
                460
        );

        // BARRA
        g2.setColor(Color.DARK_GRAY);

        g2.fillRoundRect(
                600,
                500,
                220,
                25,
                20,
                20
        );

        g2.setColor(colorNeon);

        g2.fillRoundRect(
                600,
                500,
                (int) (220 * porcentajeBloqueo),
                25,
                20,
                20
        );

        g2.setColor(Color.WHITE);

        g2.drawString(
                "Bloqueo hidráulico",
                600,
                490
        );

        // CONTROLES
        g2.drawString(
                "[R] Reiniciar",
                600,
                580
        );

        g2.drawString(
                "[L] Modo lluvia",
                600,
                610
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        faseOndas += 0.08;

        actualizarSimulacion();

        repaint();
    }

    private void actualizarSimulacion() {

        porcentajeBloqueo =
                (double) basuraRecolectada
                        / Math.max(basuraTotal, 1);

        double factorBloqueo =
                1 - (porcentajeBloqueo * 0.45);

        caudal =
                velocidadMedia
                        * areaHastaY
                        * factorBloqueo;

        // ESTADOS
        if (porcentajeBloqueo < 0.3) {

            estadoSistema = "NORMAL";

        } else if (porcentajeBloqueo < 0.6) {

            estadoSistema = "PRECAUCION";

        } else {

            estadoSistema = "PELIGRO";
        }

        for (Basura b : basura) {

            if (!b.recolectada) {

                double profundidadRelativa =
                        (b.y - nivelAgua)
                                / alturaAgua;

                double velocidadLocal =
                        velocidadMedia *
                                (1
                                        - profundidadRelativa
                                        * 0.3);

                double turbulencia =
                        (random.nextDouble() - 0.5)
                                * nivelTurbulencia;

                b.x += velocidadLocal + turbulencia;

                b.y += b.vy;

                if (b.y <= nivelAgua + 2) {

                    b.y = nivelAgua + 2;

                    b.vy =
                            Math.abs(b.vy)
                                    + random.nextDouble()
                                    * 0.3;
                }

                if (b.y >= nivelAgua + alturaAgua - 16) {

                    b.y =
                            nivelAgua
                                    + alturaAgua
                                    - 16;

                    b.vy =
                            -Math.abs(b.vy)
                                    - random.nextDouble()
                                    * 0.3;
                }

                // RECOLECCIÓN
                if (
                        b.x >= 600 &&
                                b.x <= 620 &&
                                b.y > nivelAgua
                ) {

                    b.recolectada = true;

                    basuraRecolectada++;
                }

                if (b.x > 50 + anchoCanal) {

                    b.recolectada = true;
                }
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {

        // REINICIAR
        if (e.getKeyCode() == KeyEvent.VK_R) {

            generarBasura();

            calcularArea();

            repaint();
        }

        // MODO LLUVIA
        if (e.getKeyCode() == KeyEvent.VK_L) {

            modoLluvia = !modoLluvia;

            if (modoLluvia) {

                velocidadAgua = 4.5;

                alturaAgua = 140;

            } else {

                velocidadAgua = 2.0;

                alturaAgua = 100;
            }

            calcularArea();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    class Basura {

        double x;

        double y;

        double vy;

        boolean recolectada = false;

        public Basura(
                double x,
                double y,
                double vy
        ) {

            this.x = x;

            this.y = y;

            this.vy = vy;
        }
    }

    public static void main(String[] args) {

        JFrame ventana =
                new JFrame(
                        "Box Culvert Inteligente"
                );

        SimulacionBoxCulvert panel =
                new SimulacionBoxCulvert();

        ventana.add(panel);

        ventana.pack();

        ventana.setLocationRelativeTo(null);

        ventana.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE
        );

        ventana.setVisible(true);
    }
}