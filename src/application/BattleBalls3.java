package application;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.*;
import java.util.*;

public class BattleBalls3 extends Application {

    // ── Constantes ──────────────────────────────────────────────────────────
    private static final int    ARENA_W          = 960;
    private static final int    ARENA_H          = 720;
    private static final int    PHYSICS_SUBSTEPS = 160;
    private static final double GRAVITY          = 0.20;
    private static final double MAX_SPEED        = 1000.0;
    static final         double DEFAULT_RADIUS   = 75.0;

    // ── FPS ───────────────────────────────────────────────────────────────
    private long    lastUpdateTime = 0;
    private double  fps            = 0;
    private final long[] frameTimes = new long[60];
    private int     frameTimeIndex  = 0;
    private boolean arrayFilled     = false;

    // ── estado de Jogo ────────────────────────────────────────────────────────
    private GraphicsContext      gc;
    private final List<Ball>         balls         = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<Explosion>    explosions    = new ArrayList<>();
    private final Random             random        = new Random();
    private boolean  gameRunning = false;
    private AnimationTimer gameLoop;
    private Stage hudStage;

    // configuração de times e "Type:HP:mode" de cada um dos objetos (modo = normal | super | hiper)
    private final List<String> configTeam1 = new ArrayList<>();
    private final List<String> configTeam2 = new ArrayList<>();

    // ── Referencias de UI  ───────────────────────────────────────────────────────────
    private Label       lblName1, lblName2;
    private ProgressBar pbTeam1, pbTeam2;
    private Button      btnPrepare;
    private Label       lblGameInfo;
    private String      overlayStats1 = "", overlayStats2 = "";
    
    // main
    public static void main(String[] args) { 
    	launch(args); 
    	
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#0A0A0A;");
        root.setTop(buildTopBar());
        root.setCenter(buildCenterArea());
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, ARENA_W + 40, ARENA_H + 180);
        scene.getRoot().setStyle("-fx-base:#121212; -fx-control-inner-background:#1A1A1A;");
        stage.setTitle("⚔  Battle Balls 3.0");
        stage.setScene(scene);
        stage.show();

        render();

        gameLoop = new AnimationTimer() {
            @Override public void handle(long now) {
                trackFPS(now);
                if (gameRunning) {
                    updatePhysics();
                    updateFloatingTexts();
                    updateExplosions();
                    updateOverlayStats();
                    checkWinCondition();
                }
                render();
            }
        };
        gameLoop.start();
        
        initPrepareHUD();
    }

    //  Construtores de layout

    private HBox buildTopBar() {
        HBox bar = new HBox(28);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(12, 24, 12, 24));
        bar.setStyle("-fx-background-color:#060606; -fx-border-color:#1C1C1C; -fx-border-width:0 0 2 0;");

        lblName1 = lbl("TIME 1", "#4FC3F7", "Impact", 26);
        pbTeam1  = mkPB("#4FC3F7", 230);
        VBox v1  = vb(Pos.CENTER, 4, lblName1, pbTeam1);

        Label vs = lbl("⚔", "#333333", "Arial", 28);

        lblName2 = lbl("TIME 2", "#EF5350", "Impact", 26);
        pbTeam2  = mkPB("#EF5350", 230);
        VBox v2  = vb(Pos.CENTER, 4, lblName2, pbTeam2);

        bar.getChildren().addAll(v1, vs, v2);
        return bar;
    }

    private StackPane buildCenterArea() {
        Canvas canvas = new Canvas(ARENA_W, ARENA_H);
        gc = canvas.getGraphicsContext2D();
        StackPane sp = new StackPane(canvas);
        sp.setStyle("-fx-background-color:#0D0D0D;");
        sp.setAlignment(Pos.CENTER);
        return sp;
    }

    private HBox buildBottomBar() {
        HBox bar = new HBox(20);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(16));
        bar.setStyle("-fx-background-color:#060606; -fx-border-color:#1C1C1C; -fx-border-width:2 0 0 0;");

        lblGameInfo = lbl("Configure os times e inicie a batalha.", "#555555", "Verdana", 12);

        btnPrepare = new Button("⚔   PREPARAR BATALHA");
        styleGreen(btnPrepare);
        btnPrepare.setOnAction(e -> { if (gameRunning) cancelBattle(); else initPrepareHUD(); });

        bar.getChildren().addAll(lblGameInfo, btnPrepare);
        return bar;
    }

    // Preparando a HUD (para pop-up)

    private void initPrepareHUD() {
        hudStage = new Stage();
        hudStage.setTitle("⚔  Preparar Batalha");
        hudStage.setResizable(false);

        // referencia de atributos mutaveis (truque de array para lambdas)
        final String[] selType = {"Desarmado"};
        final String[] selMode = {"normal"};

        // Referencias do painel central
        final Label[]  mName  = { lbl(getBallDisplayName(selType[0]), getBallColorHex(selType[0]), "Impact", 22) };
        final Label[]  mDesc  = { wrapLbl(getBallDescription(selType[0]), "#888888", 11, 290) };
        final Label[]  mSpec  = { wrapLbl(getBallSpecialAbility(selType[0]), "#BBBBBB", 11, 290) };
        final Canvas[] radar  = { new Canvas(290, 260) };
        final Canvas[] prev   = { new Canvas(180, 180) };

        // desenhando o grafico de radar
        drawRadarChart(radar[0].getGraphicsContext2D(), selType[0]);

        StackPane prevPane = new StackPane(prev[0]);
        prevPane.setStyle("-fx-background-color:#0A0A0A; -fx-border-color:#222; -fx-border-width:2;");

        Label specTitle = lbl("✦  Habilidade Especial", "#FFD700", "Verdana", 12);
        VBox specBox = new VBox(5, specTitle, mSpec[0]);
        specBox.setStyle("-fx-background-color:#111111; -fx-border-color:#2A2A2A; -fx-border-width:1; -fx-padding:10;");

        VBox middlePane = new VBox(8, mName[0], mDesc[0], prevPane, radar[0], specBox);
        middlePane.setAlignment(Pos.TOP_CENTER);
        middlePane.setPadding(new Insets(12, 10, 12, 10));
        middlePane.setPrefWidth(310);
        middlePane.setStyle("-fx-background-color:#080808; -fx-border-color:#1C1C1C; -fx-border-width:0 1 0 1;");

        // animando a "preview" de cada uma das bolas
        final double[] ps = {90, 90, 1.8, 1.1, 0};
        AnimationTimer previewTimer = new AnimationTimer() {
            long last = 0;
            @Override public void handle(long now) {
                if (last == 0) { last = now; return; }
                double dt = (now - last) / 1e9; last = now;
                ps[4] += 150 * dt;
                ps[0] += ps[2]; ps[1] += ps[3];
                double r = 42;
                if (ps[0]-r < 0 || ps[0]+r > 180) ps[2] = -ps[2];
                if (ps[1]-r < 0 || ps[1]+r > 180) ps[3] = -ps[3];
                drawPreview(prev[0].getGraphicsContext2D(), selType[0], ps[0], ps[1], r, ps[4]);
            }
        };
        previewTimer.start();
        hudStage.setOnHidden(e -> previewTimer.stop());

        // grid para o index das bolas
        String[] ALL = {"Desarmado","Espada","Crescedor","Acelerado","Adaga","Centy","Tanque","Vampiro","Bomba","Espinho","Mercurio"};
        GridPane grid = new GridPane();
        grid.setHgap(5); grid.setVgap(5);

        for (int i = 0; i < ALL.length; i++) {
            Button btn = new Button(getBallIcon(ALL[i]) + "  " + ALL[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle(cardStyle(ALL[i], i == 0));
            btn.setUserData(ALL[i]);
            grid.add(btn, i % 2, i / 2);
            GridPane.setHgrow(btn, Priority.ALWAYS);
        }

        // hook ações de click para após a saida de todos os botões
        for (Node node : grid.getChildren()) {
            Button btn = (Button) node;
            String t = (String) btn.getUserData();
            btn.setOnAction(e -> {
                selType[0] = t;
                mName[0].setText(getBallDisplayName(t));
                mName[0].setStyle("-fx-text-fill:"+getBallColorHex(t)+"; -fx-font-family:Impact; -fx-font-size:22px;");
                mDesc[0].setText(getBallDescription(t));
                mSpec[0].setText(getBallSpecialAbility(t));
                drawRadarChart(radar[0].getGraphicsContext2D(), t);
                for (Node n : grid.getChildren()) {
                    Button b2 = (Button) n;
                    b2.setStyle(cardStyle((String) b2.getUserData(), b2.getUserData().equals(t)));
                }
            });
        }

        // HP Modo e cores
        Label hpLbl  = secLbl("HP INICIAL:");
        Spinner<Integer> spnHp = new Spinner<>(10, 500000, 100, 10);
        spnHp.setEditable(true); spnHp.setMaxWidth(Double.MAX_VALUE);
        spnHp.getEditor().setStyle("-fx-background-color:#1A1A1A; -fx-text-fill:white;");

        Label modeLbl = secLbl("MODO:");
        ToggleGroup tg = new ToggleGroup();
        RadioButton rbN = radio("Normal",          "normal", tg, true);
        RadioButton rbS = radio("⚡ Super  (5×)",  "super",  tg, false);
        RadioButton rbH = radio("💀 Hiper  (10×)", "hiper",  tg, false);
        rbN.setOnAction(e -> selMode[0] = "normal");
        rbS.setOnAction(e -> selMode[0] = "super");
        rbH.setOnAction(e -> selMode[0] = "hiper");
        VBox modeVBox = new VBox(5, rbN, rbS, rbH);

        // Adicionando botões
        Button bT1 = new Button("➕  Adicionar ao Time 1");
        bT1.setMaxWidth(Double.MAX_VALUE);
        bT1.setStyle("-fx-background-color:#0D2035; -fx-text-fill:#4FC3F7; -fx-font-weight:bold;" +
                     "-fx-border-color:#4FC3F7; -fx-border-width:1; -fx-cursor:hand;");

        Button bT2 = new Button("➕  Adicionar ao Time 2");
        bT2.setMaxWidth(Double.MAX_VALUE);
        bT2.setStyle("-fx-background-color:#200D0D; -fx-text-fill:#EF5350; -fx-font-weight:bold;" +
                     "-fx-border-color:#EF5350; -fx-border-width:1; -fx-cursor:hand;");

        // Lista de times (espelhando listas anteriores)
        ListView<String> lst1 = styledLV(configTeam1);
        ListView<String> lst2 = styledLV(configTeam2);

        bT1.setOnAction(e -> {
            String cfg = selType[0] + ":" + spnHp.getValue() + ":" + selMode[0];
            configTeam1.add(cfg);
            lst1.getItems().add(getBallIcon(selType[0]) + " " + selType[0] + "  HP:" + spnHp.getValue() + " [" + selMode[0] + "]");
            
            // Preview Visual: Cria a bola, joga ela do lado esquerdo da arena e adiciona à lista
            Ball previewBall = configToBall(cfg, 1);
            doSpawn(previewBall, 1); 
            balls.add(previewBall);
        });

        bT2.setOnAction(e -> {
            String cfg = selType[0] + ":" + spnHp.getValue() + ":" + selMode[0];
            configTeam2.add(cfg);
            lst2.getItems().add(getBallIcon(selType[0]) + " " + selType[0] + "  HP:" + spnHp.getValue() + " [" + selMode[0] + "]");
            
            // Preview Visual: Cria a bola, joga ela do lado direito da arena e adiciona à lista
            Ball previewBall = configToBall(cfg, 2);
            doSpawn(previewBall, 2);
            balls.add(previewBall);
        });

        VBox leftPane = new VBox(8,
            secLbl("ÍNDICE DE BOLAS"), grid,
            new Separator(),
            hpLbl, spnHp,
            modeLbl, modeVBox,
            new Separator(),
            bT1, bT2
        );
        leftPane.setPadding(new Insets(14, 10, 14, 14));
        leftPane.setPrefWidth(235);
        leftPane.setStyle("-fx-background-color:#0A0A0A;");

        // Painel direito
        Label t1lbl = lbl("● TIME 1  (AZUL)",     "#4FC3F7", "Verdana", 12);
        Label t2lbl = lbl("● TIME 2  (VERMELHO)",  "#EF5350", "Verdana", 12);

        Button clr1 = smallBtn("Limpar T1", "#4FC3F7");
        Button clr2 = smallBtn("Limpar T2", "#EF5350");
        clr1.setOnAction(e -> { configTeam1.clear(); lst1.getItems().clear(); });
        clr2.setOnAction(e -> { configTeam2.clear(); lst2.getItems().clear(); });
        HBox clrRow = new HBox(6, clr1, clr2);
        HBox.setHgrow(clr1, Priority.ALWAYS); clr1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clr2, Priority.ALWAYS); clr2.setMaxWidth(Double.MAX_VALUE);

        Button btnStart = new Button("⚔   INICIAR BATALHA");
        btnStart.setMaxWidth(Double.MAX_VALUE);
        btnStart.setStyle("-fx-background-color:linear-gradient(to bottom,#27AE60,#1A6E3A);" +
                          "-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;" +
                          "-fx-background-radius:5; -fx-cursor:hand; -fx-padding:10 0;");

        Label hudInfo = new Label("Configure os times acima.");
        hudInfo.setStyle("-fx-text-fill:#555; -fx-font-size:10px;");
        hudInfo.setWrapText(true);

        btnStart.setOnAction(e -> {
            if (configTeam1.isEmpty() || configTeam2.isEmpty()) {
                hudInfo.setText("⚠  Adicione bolas nos dois times!"); return;
            }
            hudStage.close();
            startGame();
        });

        VBox rightPane = new VBox(8,
            t1lbl, lst1, t2lbl, lst2, clrRow, new Separator(), btnStart, hudInfo
        );
        rightPane.setPadding(new Insets(14, 14, 14, 10));
        rightPane.setPrefWidth(235);
        rightPane.setStyle("-fx-background-color:#0A0A0A;");

        HBox root = new HBox(leftPane, middlePane, rightPane);
        Scene sc = new Scene(root);
        sc.getRoot().setStyle("-fx-base:#121212; -fx-control-inner-background:#1A1A1A;");
        hudStage.setScene(sc);

        // initial draw
        drawPreview(prev[0].getGraphicsContext2D(), selType[0], 90, 90, 42, 0);
        hudStage.show();
    }

    // CONTROLE DO JOGO // PARTIDA

    private void startGame() {
        balls.clear(); floatingTexts.clear(); explosions.clear();
        gameRunning = true;
        lblName1.setText(formatNames(configTeam1));
        lblName2.setText(formatNames(configTeam2));
        pbTeam1.setProgress(1.0); pbTeam2.setProgress(1.0);
        lblGameInfo.setText("⚔  Batalha em andamento!");
        btnPrepare.setText("✖   CANCELAR BATALHA"); styleRed(btnPrepare);

        for (String cfg : configTeam1) balls.add(doSpawn(configToBall(cfg, 1), 1));
        for (String cfg : configTeam2) balls.add(doSpawn(configToBall(cfg, 2), 2));
    }

    private void cancelBattle() {
        balls.clear(); floatingTexts.clear(); explosions.clear();
        gameRunning = false; overlayStats1 = ""; overlayStats2 = "";
        pbTeam1.setProgress(0); pbTeam2.setProgress(0);
        lblGameInfo.setText("⚖  Batalha cancelada — EMPATE!");
        btnPrepare.setText("⚔   PREPARAR BATALHA"); styleGreen(btnPrepare);
    }

    private void endGame(String msg) {
        gameRunning = false;
        lblGameInfo.setText(msg);
        btnPrepare.setText("⚔   PREPARAR BATALHA"); styleGreen(btnPrepare);
    }

    private Ball configToBall(String cfg, int team) {
        String[] p = cfg.split(":");
        double mult = p.length > 2 ? (p[2].equals("hiper") ? 10 : p[2].equals("super") ? 5 : 1) : 1;
        Ball b = createBall(p[0], 0, 0);
        b.setCustomStats(team, Integer.parseInt(p[1]));
        if (mult > 1) b.applyMode(mult);
        return b;
    }

    private Ball doSpawn(Ball b, int side) {
        int m = (int)(b.radius + 10);
        b.x = side == 1
            ? m + random.nextInt(Math.max(1, ARENA_W / 2 - m * 2))
            : ARENA_W / 2 + m + random.nextInt(Math.max(1, ARENA_W / 2 - m * 2));
        b.y = m + random.nextInt(Math.max(1, ARENA_H - m * 2));
        double spd = 4 + random.nextDouble() * 3;
        double ang = random.nextDouble() * Math.PI * 2;
        b.vx = Math.cos(ang) * spd; b.vy = Math.sin(ang) * spd;
        return b;
    }

    private Ball createBall(String type, double x, double y) {
        switch (type) {
            case "Espada":    return new SwordBall(x, y);
            case "Crescedor": return new GrowerBall(x, y);
            case "Acelerado": return new SpeedBall(x, y);
            case "Adaga":     return new DaggerBall(x, y);
            case "Centy":     return new Centy(x, y);
            case "Tanque":    return new TankBall(x, y);
            case "Vampiro":   return new VampireBall(x, y);
            case "Bomba":     return new BombBall(x, y);
            case "Espinho":   return new ThornBall(x, y);
            case "Mercurio":  return new MercuryBall(x, y);
            default:          return new UnarmedBall(x, y);
        }
    }

    private String formatNames(List<String> list) {
        if (list.isEmpty()) return "VAZIO";
        List<String> ns = new ArrayList<>();
        for (String c : list) ns.add(getBallIcon(c.split(":")[0]));
        return String.join(" ", ns);
    }

    // FISICAS (MAY GOD HELP US CUZ I DONT KNOW A FUCKING THING)

    private void updatePhysics() {
        double step = 1.0 / PHYSICS_SUBSTEPS;

        for (Ball b : balls) {
            if (b.hitCooldownFrames  > 0) b.hitCooldownFrames--;
            if (b.damageFlashFrames  > 0) b.damageFlashFrames--;
            if (b.healFlashFrames    > 0) b.healFlashFrames--;
        }
        for (Ball b : balls) b.onFrame(balls);

        for (int s = 0; s < PHYSICS_SUBSTEPS; s++) {

            // Mover
            for (Ball b : balls) {
                b.vy += GRAVITY * step;
                double sp = Math.sqrt(b.vx*b.vx + b.vy*b.vy);
                if (sp > MAX_SPEED) { b.vx = b.vx/sp*MAX_SPEED; b.vy = b.vy/sp*MAX_SPEED; }
                
                b.x += b.vx * step;
                b.y += b.vy * step;
                b.checkWallCollision(ARENA_W, ARENA_H);

                if (b instanceof SwordBall)  { SwordBall  sb = (SwordBall)  b; sb.angle(step); for (Ball t : balls) if (b!=t && b.teamId!=t.teamId) sb.swordCheck(t);  }
                if (b instanceof DaggerBall) { DaggerBall db = (DaggerBall) b; db.angle(step); for (Ball t : balls) if (b!=t && b.teamId!=t.teamId) db.daggerCheck(t); }
            }

            // impacto de bola com bola
            for (int i = 0; i < balls.size(); i++) {
                for (int j = i+1; j < balls.size(); j++) {
                    Ball a = balls.get(i), b = balls.get(j);
                    if (!overlapping(a, b)) continue;

                    double v1x=a.vx, v1y=a.vy, v2x=b.vx, v2y=b.vy;
                    double dx=b.x-a.x, dy=b.y-a.y;
                    double d=Math.sqrt(dx*dx+dy*dy); if(d==0) d=.001;
                    double nx=dx/d, ny=dy/d;
                    double imp1 = v1x*nx + v1y*ny;
                    double imp2 = v2x*(-nx) + v2y*(-ny);
                    resolveCollision(a, b);

                    if (a.teamId != b.teamId && a.hitCooldownFrames <= 0 && b.hitCooldownFrames <= 0) {
                        if (imp1 > imp2 && imp1 > 0) {
                            b.lastAttacker = a;
                            double bef = b.hp; a.onHit(b);
                            double dealt = bef - b.hp;
                            if (dealt > 0) { spawnFloat(b.x, b.y-26, "-"+(int)dealt, tColor(a.teamId)); if (a instanceof VampireBall) ((VampireBall)a).vampHeal(dealt*0.5); }
                            a.hitCooldownFrames = a.attackCooldownFrames;
                        } else if (imp2 > imp1 && imp2 > 0) {
                            a.lastAttacker = b;
                            double bef = a.hp; b.onHit(a);
                            double dealt = bef - a.hp;
                            if (dealt > 0) { spawnFloat(a.x, a.y-26, "-"+(int)dealt, tColor(b.teamId)); if (b instanceof VampireBall) ((VampireBall)b).vampHeal(dealt*0.5); }
                            b.hitCooldownFrames = b.attackCooldownFrames;
                        }
                    }
                }
            }
        }
    }

    private boolean overlapping(Ball a, Ball b) {
        double dx=a.x-b.x, dy=a.y-b.y, r=a.radius+b.radius; return dx*dx+dy*dy < r*r;
    }

    private void resolveCollision(Ball a, Ball b) {
        double dx=b.x-a.x, dy=b.y-a.y;
        double d=Math.sqrt(dx*dx+dy*dy); if(d==0) d=.1;
        double nx=dx/d, ny=dy/d;
        double push = -0.5*(d - a.radius - b.radius); // positive when overlapping
        a.x -= push*nx; a.y -= push*ny;
        b.x += push*nx; b.y += push*ny;
        double kx=a.vx-b.vx, ky=a.vy-b.vy;
        double p=2.0*(nx*kx+ny*ky)/(a.mass+b.mass);
        a.vx -= p*b.mass*nx; a.vy -= p*b.mass*ny;
        b.vx += p*a.mass*nx; b.vy += p*a.mass*ny;
    }

    private Color tColor(int t) { return t==1 ? Color.CYAN : Color.ORANGERED; }

    private void spawnFloat(double x, double y, String text, Color c) { floatingTexts.add(new FloatingText(x, y, text, c)); }

    private void updateFloatingTexts() { floatingTexts.removeIf(ft->ft.alpha<=0); for (FloatingText ft:floatingTexts){ft.y-=0.9;ft.alpha-=0.020;} }
    private void updateExplosions()    { explosions.removeIf(ex->ex.alpha<=0);    for (Explosion ex:explosions){ex.r+=ex.growRate;ex.alpha-=0.040;} }

    private void trackFPS(long now) {
        if (lastUpdateTime > 0) {
            frameTimes[frameTimeIndex] = now - lastUpdateTime;
            frameTimeIndex = (frameTimeIndex + 1) % frameTimes.length;
            if (frameTimeIndex == 0) arrayFilled = true;
            long tot=0; int cnt=arrayFilled?frameTimes.length:frameTimeIndex;
            for (int i=0;i<cnt;i++) tot+=frameTimes[i];
            if (tot > 0) fps = 2_000_000_000.0 / (tot/(double)cnt);
        }
        lastUpdateTime = now;
    }

    private void updateOverlayStats() {
        StringBuilder s1=new StringBuilder(), s2=new StringBuilder();
        double tHp1=0,mHp1=0,tHp2=0,mHp2=0;
        for (Ball b : balls) {
            if (b.teamId==1) { s1.append(b.getStatsText()).append("\n"); tHp1+=Math.max(0,b.hp); mHp1+=b.maxHp; }
            else             { s2.append(b.getStatsText()).append("\n"); tHp2+=Math.max(0,b.hp); mHp2+=b.maxHp; }
        }
        overlayStats1 = s1.toString();
        overlayStats2 = s2.toString();
        if (mHp1>0) pbTeam1.setProgress(tHp1/mHp1);
        if (mHp2>0) pbTeam2.setProgress(tHp2/mHp2);
    }

    private void checkWinCondition() {
        balls.removeIf(b -> b.hp <= 0);
        boolean t1=balls.stream().anyMatch(b->b.teamId==1);
        boolean t2=balls.stream().anyMatch(b->b.teamId==2);
        if (!t1&&!t2) endGame("⚖  EMPATE TOTAL!");
        else if (!t1) endGame("🏆  VITÓRIA — TIME 2  (VERMELHO)!");
        else if (!t2) endGame("🏆  VITÓRIA — TIME 1  (AZUL)!");
    }

    // Renderizando a porra toda

    private void render() {
        // Background
        gc.setFill(Color.web("#0E0E0E")); gc.fillRect(0,0,ARENA_W,ARENA_H);

        // preenchendo cada uma das bolas com cores respectivas
        gc.setFill(Color.web("#181818"));
        for (int gx=48;gx<ARENA_W;gx+=48) for (int gy=48;gy<ARENA_H;gy+=48) gc.fillOval(gx-1.5,gy-1.5,3,3);

        // Dividindo o centro para o spawn das bolas em seus lados respectivos
        gc.setStroke(Color.web("#1E1E1E")); gc.setLineWidth(1); gc.setLineDashes(9,6);
        gc.strokeLine(ARENA_W/2.0,0,ARENA_W/2.0,ARENA_H); gc.setLineDashes(null);

        // Explosões (atras das bolas papai)
        for (Explosion ex : explosions) {
            gc.save();
            gc.setGlobalAlpha(ex.alpha * 0.55); gc.setStroke(ex.color); gc.setLineWidth(3);
            gc.strokeOval(ex.x-ex.r, ex.y-ex.r, ex.r*2, ex.r*2);
            gc.setGlobalAlpha(ex.alpha * 0.12); gc.setFill(ex.color);
            gc.fillOval(ex.x-ex.r, ex.y-ex.r, ex.r*2, ex.r*2);
            gc.restore();
        }

        // B O L A
        for (Ball b : balls) b.draw(gc);

        // Textos flutuantes
        for (FloatingText ft : floatingTexts) {
            gc.save(); gc.setGlobalAlpha(Math.max(0,ft.alpha));
            gc.setFont(Font.font("Verdana",FontWeight.BOLD,14));
            gc.setTextAlign(TextAlignment.CENTER); gc.setTextBaseline(VPos.BASELINE);
            gc.setFill(Color.color(0,0,0,0.6)); gc.fillText(ft.text,ft.x+1,ft.y+1);
            gc.setFill(ft.color); gc.fillText(ft.text,ft.x,ft.y);
            gc.restore();
        }

        // Overlay's de status
        if (!overlayStats1.isEmpty()) drawStatOverlay(overlayStats1, 10, 10, Color.web("#4FC3F7"), TextAlignment.LEFT);
        if (!overlayStats2.isEmpty()) drawStatOverlay(overlayStats2, ARENA_W-10, 10, Color.web("#EF5350"), TextAlignment.RIGHT);

        // FPS
        gc.save(); gc.setFont(Font.font("Monospaced",FontWeight.BOLD,11));
        gc.setFill(Color.web("#333")); gc.setTextAlign(TextAlignment.RIGHT); gc.setTextBaseline(VPos.TOP);
        gc.fillText(String.format("FPS: %.0f",fps),ARENA_W-6,4); gc.restore();
    }

    private void drawStatOverlay(String text, double x, double y, Color color, TextAlignment align) {
        String[] lines = text.split("\n");
        double bgW = 190, bgH = lines.length * 14 + 8;
        double bgX = align == TextAlignment.LEFT ? x-4 : x - bgW + 4;
        gc.save();
        gc.setFill(Color.color(0,0,0,0.55)); gc.fillRoundRect(bgX, y-4, bgW, bgH, 5, 5);
        gc.setFont(Font.font("Verdana",FontWeight.BOLD,11));
        gc.setFill(color); gc.setTextAlign(align); gc.setTextBaseline(VPos.TOP);
        for (int i=0;i<lines.length;i++) gc.fillText(lines[i], x, y + i*14);
        gc.restore();
    }

    // Grafico de radar + preview na arena
    private void drawRadarChart(GraphicsContext g, String type) {
        final double cx=145, cy=130, MAX_R=95;
        final int N=5;
        final String[] axes = {"ATQ","VEL","DEF","TAM","ESPEC"};
        final double[] vals = getBallStats(type);
        final Color    col  = getBallColor(type);

        g.setFill(Color.web("#0D0D0D")); g.fillRect(0,0,290,260);

        for (int ring=1;ring<=4;ring++) {
            double rr=MAX_R*ring/4.0; double[] wx=new double[N],wy=new double[N];
            for (int i=0;i<N;i++){double a=Math.toRadians(-90+360.0/N*i);wx[i]=cx+Math.cos(a)*rr;wy[i]=cy+Math.sin(a)*rr;}
            g.setStroke(Color.web(ring==4?"#2E2E2E":"#1A1A1A")); g.setLineWidth(1); g.strokePolygon(wx,wy,N);
        }
        for (int i=0;i<N;i++){double a=Math.toRadians(-90+360.0/N*i);g.setStroke(Color.web("#222"));g.strokeLine(cx,cy,cx+Math.cos(a)*MAX_R,cy+Math.sin(a)*MAX_R);}

        double[] dx=new double[N],dy=new double[N];
        for (int i=0;i<N;i++){double a=Math.toRadians(-90+360.0/N*i);double r=MAX_R*vals[i];dx[i]=cx+Math.cos(a)*r;dy[i]=cy+Math.sin(a)*r;}
        g.setFill(col.deriveColor(0,1,1,0.20)); g.fillPolygon(dx,dy,N);
        g.setStroke(col); g.setLineWidth(2); g.strokePolygon(dx,dy,N);
        for (int i=0;i<N;i++){g.setFill(col.brighter());g.fillOval(dx[i]-4,dy[i]-4,8,8);}

        g.setFont(Font.font("Verdana",FontWeight.BOLD,11));
        g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
        double maxV=0; for(double v:vals) maxV=Math.max(maxV,v);
        for (int i=0;i<N;i++){
            double a=Math.toRadians(-90+360.0/N*i);
            g.setFill(vals[i]>=maxV?col:Color.web("#666666"));
            g.fillText(axes[i],cx+Math.cos(a)*(MAX_R+16),cy+Math.sin(a)*(MAX_R+16));
        }
        g.setFont(Font.font("Verdana",8)); g.setFill(Color.web("#444")); g.setTextBaseline(VPos.BOTTOM);
        for (int i=0;i<N;i++) g.fillText((int)(vals[i]*100)+"%",dx[i],dy[i]-5);
    }

    private void drawPreview(GraphicsContext g, String type, double bx, double by, double r, double angle) {
        g.setFill(Color.web("#0A0A0A")); g.fillRect(0,0,180,180);
        Color c = getBallColor(type);
        for (int i=5;i>=1;i--) { g.setGlobalAlpha(0.05*i); g.setFill(c); g.fillOval(bx-r-i*5,by-r-i*5,(r+i*5)*2,(r+i*5)*2); }
        g.setGlobalAlpha(1.0);
        g.setFill(c); g.fillOval(bx-r,by-r,r*2,r*2);
        g.setStroke(c.brighter()); g.setLineWidth(2); g.strokeOval(bx-r,by-r,r*2,r*2);
        g.setFill(Color.color(1,1,1,0.18)); g.fillOval(bx-r*0.52,by-r*0.80,r*0.56,r*0.44);
        if (type.equals("Espada")||type.equals("Adaga")) {
            double len = type.equals("Espada") ? 44 : 32;
            g.save(); g.translate(bx,by); g.rotate(angle);
            g.setFill(Color.DARKSLATEGRAY); g.fillRect(r,-3,len,7);
            g.setFill(Color.web("#8B4513")); g.fillRect(r-4,-3,8,7);
            g.restore();
        }
        g.setFill(Color.WHITE); g.setFont(Font.font("Verdana",FontWeight.BOLD,10));
        g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER); g.fillText(type,bx,by);
        g.setGlobalAlpha(1.0);
    }

    // Ajudantes para informações das bolas (desc,tipo,display,etc)
    String getBallIcon(String t) {
        switch(t){case"Espada":return"🗡";case"Crescedor":return"🌱";case"Acelerado":return"⚡";case"Mercurio":return"⚡";
                   case"Adaga":return"🔪";case"Centy":return"☠";case"Tanque":return"🛡";
                   case"Vampiro":return"🦇";case"Bomba":return"💣";case"Espinho":return"🌵";default:return"👊";}
    }
    String getBallDisplayName(String t) {
        switch(t){case"Espada":return"Guerreiro de Espada";case"Crescedor":return"Crescedor";
                   case"Acelerado":return"Acelerado";case"Mercury":return"Mercurio";case"Adaga":return"Adaga";case"Centy":return"Centésimo";
                   case"Tanque":return"Tanque";case"Vampiro":return"Vampiro";case"Bomba":return"Bombardeiro";
                   case"Espinho":return"Espinhoso";default:return"Desarmado";}
    }
    String getBallDescription(String t) {
        switch(t){
            case"Espada":    return"Espada orbital que aumenta o dano a cada golpe.";
            case"Crescedor": return"Cresce após cada colisão. Quanto maior, mais pesado e devastador.";
            case"Acelerado": return"Ganha velocidade e dano a cada colisão. Gera raios elétricos visuais.";
            case"Mercurio": 	 return"Ganha Muita velocidade a cada colisão. O apice da velocidade.";
            case"Adaga":     return"Rotação extremamente rápida. Fica mais letal a cada acerto";
            case"Centy":     return"Dano percentual: acada golpe causa uma porcentagem da vida maxima do oponnte.";
            case"Tanque":    return"Armadura passiva que reduz o dano recebido. Massa tripla e dano de colisão brutal.";
            case"Vampiro":   return"Rouba vida ao causar dano. Fica mais forte conforme drena os inimigos.";
            case"Bomba":     return"Explode periodicamente causando dano em área. A explosão cresce a cada detonação.";
            case"Espinho":   return"Reflete dano de volta ao atacante. A reflexão aumenta a cada hit sofrido.";
            default:         return"Evolui a cada colisão: dano e velocidade aumentam permanentemente.";
        }
    }
    String getBallSpecialAbility(String t) {
        switch(t){
            case"Espada":    return"Espada Orbital: Ponta da espada causa dano sem contato. Dano +1 por acerto acumulado.";
            case"Crescedor": return"Crescimento Orgânico : Raio +8 e massa +0.18 por colisão (cooldown 0.5s). Dano bônus = raio÷50.";
            case"Acelerado": return"Descarga de Adrenalina : Velocidade +2 por colisão. Dano extra = vel÷10.";
            case"Mercurio":   return"A Velocidade: Velocidade +5 por colisão. Dano extra = vel÷10.";
            case"Adaga":     return"Fúria Giratória : Spin rate +2 por acerto. Sem limite máximo de rotação.";
            case"Centy":     return"Dano Percentual : Aplica dmg% do HP atual do inimigo. Percentual +1% por acerto (máx 99%).";
            case"Tanque":    return"Blindagem Passiva : 35% de redução de todo dano recebido.";
            case"Vampiro":   return"Dreno de Vida : Recupera 50% do dano causado. Cada cura aumenta o dano em 0.5.";
            case"Bomba":     return"Explosão em Área : Detona a cada 3s, dano aumenta por detonação. Raio da explosão cresce.";
            case"Espinho":   return"Reflexo de Dano : Reflete 40% de todo dano recebido ao atacante. Reflexo +1% por hit sofrido.";
            default:         return"Predador Apex : Dano e velocidade +1 por colisão.";
        }
    }
    double[] getBallStats(String t) {
        switch(t){
            case"Espada":    return new double[]{0.80,0.50,0.50,0.50,0.80};
            case"Crescedor": return new double[]{0.50,0.5,0.8,1.00,0.80};
            case"Acelerado": return new double[]{0.6,0.90,0.5,0.5,0.75};
            case"Mercurio":   return new double[]{0.5,1.0,0.5,0.5,0.85};
            case"Adaga":     return new double[]{0.5,0.5,0.5,0.5,1.00};
            case"Centy":     return new double[]{1.00,0.50,0.5,0.50,1.00};
            case"Tanque":    return new double[]{0.75,0.35,1.00,0.80,0.75};
            case"Vampiro":   return new double[]{0.75,0.50,0.75,0.50,0.80};
            case"Bomba":     return new double[]{0.90,0.50,0.50,0.50,0.90};
            case"Espinho":   return new double[]{0.6,0.50,0.90,0.50,0.75};
            default:         return new double[]{1.00,0.80,0.50,0.50,0.50};
        }
    }
    Color getBallColor(String t) {
        switch(t){case"Espada":return Color.ORANGE;case"Crescedor":return Color.LIGHTGREEN;
                   case"Acelerado":return Color.web("#EF5350");case"Adaga":return Color.GOLD;
                   case"Mercurio":return Color.web("#509DEF");
                   case"Centy":return Color.web("#DDDDDD");case"Tanque":return Color.STEELBLUE;
                   case"Vampiro":return Color.MEDIUMPURPLE;case"Bomba":return Color.DARKORANGE;
                   case"Espinho":return Color.web("#4CAF50");default:return Color.web("#AAAAAA");}
    }
    String getBallColorHex(String t) {
        switch(t){case"Espada":return"#FFA040";case"Crescedor":return"#90EE90";
                   case"Acelerado":return"#EF5350";case"Adaga":return"#FFD700";
                   case"Mercurio":return "#509DEF";
                   case"Centy":return"#DDDDDD";case"Tanque":return"#4682B4";
                   case"Vampiro":return"#9370DB";case"Bomba":return"#FF8C00";
                   case"Espinho":return"#4CAF50";default:return"#AAAAAA";}
    }

    // Utilitarios da UI
    private Label lbl(String t, String c, String f, int s) {
        Label l=new Label(t); l.setStyle("-fx-text-fill:"+c+"; -fx-font-family:'"+f+"'; -fx-font-size:"+s+"px;"); return l;
    }
    private Label secLbl(String t) {
        Label l=new Label(t); l.setStyle("-fx-text-fill:#555; -fx-font-size:10px; -fx-font-weight:bold;"); return l;
    }
    private Label wrapLbl(String t, String c, int s, double w) {
        Label l=new Label(t); l.setStyle("-fx-text-fill:"+c+"; -fx-font-size:"+s+"px;"); l.setWrapText(true); l.setPrefWidth(w); return l;
    }
    private ProgressBar mkPB(String c, double w) {
        ProgressBar pb=new ProgressBar(0); pb.setPrefWidth(w);
        pb.setStyle("-fx-accent:"+c+"; -fx-background-color:#1A1A1A; -fx-pref-height:8; -fx-background-radius:4;");
        return pb;
    }
    private VBox vb(Pos p, int s, Node... nodes) { VBox v=new VBox(s); v.setAlignment(p); v.getChildren().addAll(nodes); return v; }
    private RadioButton radio(String t, String v, ToggleGroup tg, boolean sel) {
        RadioButton rb=new RadioButton(t); rb.setToggleGroup(tg); rb.setSelected(sel); rb.setStyle("-fx-text-fill:#ccc; -fx-font-size:12px;"); return rb;
    }
    private Button smallBtn(String t, String c) {
        Button b=new Button(t); b.setStyle("-fx-background-color:#111; -fx-text-fill:"+c+"; -fx-border-color:#222; -fx-border-width:1; -fx-cursor:hand;"); return b;
    }
    private ListView<String> styledLV(List<String> src) {
        ListView<String> lv=new ListView<>(); lv.setPrefHeight(105);
        lv.setStyle("-fx-control-inner-background:#0D0D0D; -fx-border-color:#1E1E1E;");
        for (String s:src){String[]p=s.split(":");lv.getItems().add(getBallIcon(p[0])+" "+p[0]+"  HP:"+p[1]+(p.length>2?" ["+p[2]+"]":""));}
        return lv;
    }
    private String cardStyle(String type, boolean sel) {
        return "-fx-background-color:"+(sel?"#1C1C3A":"#111")+"; -fx-text-fill:"+getBallColorHex(type)+";" +
               "-fx-border-color:"+(sel?getBallColorHex(type):"#222")+"; -fx-border-width:1;" +
               "-fx-font-size:11px; -fx-cursor:hand; -fx-background-radius:3; -fx-border-radius:3;";
    }
    private void styleGreen(Button b) {
        b.setStyle("-fx-background-color:linear-gradient(to bottom,#27AE60,#1A6E3A);" +
                   "-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:14px;" +
                   "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:10 26;");
    }
    private void styleRed(Button b) {
        b.setStyle("-fx-background-color:linear-gradient(to bottom,#C0392B,#7B1A1A);" +
                   "-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:14px;" +
                   "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:10 26;");
    }

    static class FloatingText { double x,y,alpha; String text; Color color; FloatingText(double x,double y,String t,Color c){this.x=x;this.y=y;text=t;color=c;alpha=1.0;} }
    static class Explosion    { double x,y,r,maxR,growRate,alpha; Color color; Explosion(double x,double y,double maxR,Color c){this.x=x;this.y=y;this.maxR=maxR;r=10;color=c;alpha=1.0;growRate=maxR/25.0;} }

    abstract class Ball {
        int    teamId;
        double x, y, vx, vy;
        double radius = DEFAULT_RADIUS;
        double hp = 100, maxHp = 100;
        double damage = 1, mass = 1.0;
        Color  baseColor;
        int    hitCooldownFrames = 0, attackCooldownFrames = 5;
        int    damageFlashFrames = 0, healFlashFrames = 0;
        Ball   lastAttacker = null;
        static final int FLASH = 5;

        Ball(double x, double y, Color c) { this.x=x; this.y=y; this.baseColor=c; }

        void setCustomStats(int team, int customHp) { teamId=team; hp=customHp; maxHp=customHp; }
        void applyMode(double mult) { maxHp *= mult; hp *= mult; }
        void addSpeed(double a) { double s=Math.sqrt(vx*vx+vy*vy); if(s==0)return; double k=(s+a)/s; vx*=k; vy*=k; }
        void takeDamage(double d) { hp-=d; damageFlashFrames=FLASH; }
        void onWallHit() {}
        void onFrame(List<Ball> all) {}
        abstract void onHit(Ball target);
        abstract String getStatsText();

        void checkWallCollision(double w, double h) {
            boolean hit=false;
            if(x-radius<0){x=radius;vx=Math.abs(vx);hit=true;} else if(x+radius>w){x=w-radius;vx=-Math.abs(vx);hit=true;}
            if(y-radius<0){y=radius;vy=Math.abs(vy);hit=true;} else if(y+radius>h){y=h-radius;vy=-Math.abs(vy);hit=true;}
            if(hit) onWallHit();
        }

        void draw(GraphicsContext g) {
            double hpR = Math.max(0, hp/maxHp);
            Color tGlow = teamId==1 ? Color.web("#4FC3F7") : Color.web("#EF5350");

            //piscar de cura
            if (healFlashFrames > 0) {
                g.save(); g.setGlobalAlpha(0.35); g.setFill(Color.LIMEGREEN);
                g.fillOval(x-radius-10,y-radius-10,(radius+10)*2,(radius+10)*2); g.restore();
            }

            // Aureolas de brilho baseadas no time
            g.save(); g.setGlobalAlpha(0.14); g.setFill(tGlow); g.fillOval(x-radius-8,y-radius-8,(radius+8)*2,(radius+8)*2);
            g.setGlobalAlpha(0.06); g.fillOval(x-radius-16,y-radius-16,(radius+16)*2,(radius+16)*2); g.restore();

            // Corpo
            g.setFill(damageFlashFrames>0 ? Color.WHITE : baseColor);
            g.fillOval(x-radius,y-radius,radius*2,radius*2);

            // Borda
            g.setStroke(damageFlashFrames>0 ? Color.YELLOW : tGlow); g.setLineWidth(2.5);
            g.strokeOval(x-radius,y-radius,radius*2,radius*2);

            // brilhinhos UHUHUHAUIHDSHDHAKSJ
            g.save(); g.setGlobalAlpha(0.18); g.setFill(Color.WHITE); g.fillOval(x-radius*0.52,y-radius*0.80,radius*0.56,radius*0.44); g.restore();

            // Barra de HP
            double bW=radius*2.2, bH=6, bX=x-bW/2, bY=y+radius+6;
            g.setFill(Color.web("#111")); g.fillRoundRect(bX,bY,bW,bH,4,4);
            Color hpCol = hpR>0.5 ? Color.color(1-(hpR-0.5)*2,1.0,0) : Color.color(1.0,hpR*2,0);
            g.setFill(hpCol); g.fillRoundRect(bX,bY,bW*hpR,bH,4,4);

            // Número de HP sob as bolas
            g.setFill(damageFlashFrames>0 ? Color.YELLOW : (baseColor.getBrightness()>0.75?Color.BLACK:Color.WHITE));
            g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
            g.setFont(Font.font("Verdana",FontWeight.BOLD,Math.max(10,radius*0.52)));
            g.fillText(String.valueOf((int)Math.max(0,hp)),x,y);
        }
    }

    // ── 1. Desarmado ──────────────────────────────────────────────────────
    class UnarmedBall extends Ball {
        private final List<double[]> trail = new ArrayList<>();
        UnarmedBall(double x, double y) { super(x,y,Color.web("#AAAAAA")); }
        @Override void onHit(Ball t) { t.takeDamage(damage); damage+=1; addSpeed(1); }
        @Override void draw(GraphicsContext g) {
            double sp=Math.sqrt(vx*vx+vy*vy); int lim=(int)Math.min(20,3+sp*0.8);
            trail.add(new double[]{x,y}); while(trail.size()>lim) trail.remove(0);
            for(int i=0;i<trail.size();i++){double[]p=trail.get(i);g.save();g.setGlobalAlpha(((double)i/trail.size())*0.35);g.setFill(Color.DEEPSKYBLUE);g.fillOval(p[0]-radius,p[1]-radius,radius*2,radius*2);g.restore();}
            super.draw(g);
        }
        @Override String getStatsText() { double sp=Math.sqrt(vx*vx+vy*vy); return String.format("👊 DANO:%.0f  VEL:%.1f",damage,sp); }
    }

    // ── 2. Espada ─────────────────────────────────────────────────────────
    class SwordBall extends Ball {
        double ang=0, len=95, sDmg=1;
        SwordBall(double x, double y) { super(x,y,Color.ORANGE); damage=0; attackCooldownFrames=12; }
        void angle(double f) { ang+=5*f; }
        @Override void onHit(Ball t) {}
        void swordCheck(Ball t) {
            double r=Math.toRadians(ang), tx=x+Math.cos(r)*(radius+len), ty=y+Math.sin(r)*(radius+len);
            double dx=tx-t.x, dy=ty-t.y;
            if(dx*dx+dy*dy<(t.radius+10)*(t.radius+10) && hitCooldownFrames<=0) {
                t.lastAttacker=this; t.takeDamage(sDmg);
                spawnFloat(t.x,t.y-28,"-"+(int)sDmg,tColor(teamId));
                sDmg++; hitCooldownFrames=attackCooldownFrames;
            }
        }
        @Override void draw(GraphicsContext g) {
            super.draw(g); g.save(); g.translate(x,y); g.rotate(ang);
            g.setFill(Color.web("#B0BEC5")); g.fillRect(radius,-4,len,9);
            g.setGlobalAlpha(0.4); g.setFill(Color.WHITE); g.fillRect(radius,-4,len,2); g.setGlobalAlpha(1.0);
            g.setFill(Color.web("#8B4513")); g.fillRect(radius-5,-5,9,11); g.restore();
        }
        @Override String getStatsText() { return String.format("🗡 DMG:%.0f",sDmg); }
    }

    // ── 3. Crescedor ─────────────────────────────────────────────────────
    class GrowerBall extends Ball {
        long lastGrow=0; static final long GROW_CD=225_000_000L; static final double MAX_R=500;
        GrowerBall(double x, double y) { super(x,y,Color.LIGHTGREEN); }
        @Override void onHit(Ball t) { t.takeDamage(damage+(radius/40.0)); grow(); }
        @Override void onWallHit() { grow(); }
        private void grow() { long now=System.nanoTime(); if(now-lastGrow>GROW_CD&&radius<MAX_R){radius+=8;mass+=0.18;lastGrow=now;} }
        @Override void draw(GraphicsContext g) {
            double pct=(radius-DEFAULT_RADIUS)/(MAX_R-DEFAULT_RADIUS);
            g.save(); g.setGlobalAlpha(0.10+pct*0.18); g.setFill(Color.LIMEGREEN); g.fillOval(x-radius-10,y-radius-10,(radius+10)*2,(radius+10)*2); g.restore();
            super.draw(g);
        }
        @Override String getStatsText() { return String.format("🌱 R:%.0f B:+%.1f",radius,radius/50.0); }
    }

    // ── 4. Acelerado ─────────────────────────────────────────────────────
    class SpeedBall extends Ball {
        private final List<double[]> trail=new ArrayList<>(); private final Random lr=new Random();
        SpeedBall(double x, double y) { super(x,y,Color.web("#EF5350")); }
        @Override void onHit(Ball t) { double sp=Math.sqrt(vx*vx+vy*vy); t.takeDamage(damage); addSpeed(2.5); if(sp>10) damage+=sp/10; }
        @Override void draw(GraphicsContext g) {
            double sp=Math.sqrt(vx*vx+vy*vy); int lim=(int)Math.min(30,4+sp*1.2);
            trail.add(new double[]{x,y}); while(trail.size()>lim) trail.remove(0);
            if(sp>2&&trail.size()>2){
                for(int i=0;i<trail.size();i++){double[]p=trail.get(i);g.save();g.setGlobalAlpha(((double)i/trail.size())*0.10);g.setFill(Color.YELLOW);g.fillOval(p[0]-radius,p[1]-radius,radius*2,radius*2);g.restore();}
                int nb=(int)(sp/1.8)+2; g.save(); g.setGlobalAlpha(0.85); g.setLineWidth(1.5);
                for(int i=0;i<nb;i++){
                    double ao=lr.nextDouble()*2*Math.PI,ro=lr.nextDouble()*radius;
                    double sx=x+Math.cos(ao)*ro,sy=y+Math.sin(ao)*ro;
                    int ti=lr.nextInt(trail.size()); double[]tp=trail.get(ti);
                    double tx=tp[0]+(lr.nextDouble()-0.5)*radius*2,ty=tp[1]+(lr.nextDouble()-0.5)*radius*2;
                    g.setStroke(Color.WHITE); jagged(g,sx,sy,tx,ty);
                    g.setStroke(Color.GOLD); g.setLineWidth(0.5); jagged(g,sx,sy,tx,ty);
                }
                g.restore();
            }
            super.draw(g);
        }
        private void jagged(GraphicsContext g,double x1,double y1,double x2,double y2){
            double d=Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)); int segs=(int)(d/14)+1;
            g.beginPath(); g.moveTo(x1,y1);
            for(int i=1;i<=segs;i++){double pr=(double)i/segs,nx=x1+(x2-x1)*pr,ny=y1+(y2-y1)*pr;if(i<segs){nx+=(lr.nextDouble()-0.5)*16;ny+=(lr.nextDouble()-0.5)*16;}g.lineTo(nx,ny);}
            g.stroke();
        }
        @Override String getStatsText() { double sp=Math.sqrt(vx*vx+vy*vy); return String.format("⚡ VEL:%.1f DMG:%.0f",sp,damage); }
    }
    
    class MercuryBall extends Ball {
        private final List<double[]> trail=new ArrayList<>(); private final Random lr=new Random();
        MercuryBall(double x, double y) { super(x,y,Color.web("#509DEF")); }
        @Override void onHit(Ball t) { double sp=Math.sqrt(vx*vx+vy*vy); t.takeDamage(damage); addSpeed(5); if(sp>10) damage+=sp/10; }
        @Override void draw(GraphicsContext g) {
            double sp=Math.sqrt(vx*vx+vy*vy); int lim=(int)Math.min(30,4+sp*1.2);
            trail.add(new double[]{x,y}); while(trail.size()>lim) trail.remove(0);
            if(sp>2&&trail.size()>2){
                for(int i=0;i<trail.size();i++){double[]p=trail.get(i);g.save();g.setGlobalAlpha(((double)i/trail.size())*0.10);g.setFill(Color.AQUA);g.fillOval(p[0]-radius,p[1]-radius,radius*2,radius*2);g.restore();}
                double angle = Math.atan2(vy, vx);
                double len = 20 + sp * 2;

                g.save();
                g.setStroke(Color.web("#BFE9FF"));
                g.setGlobalAlpha(0.6);
                g.setLineWidth(2);

                for (int i = 0; i < 5; i++) {
                    double offset = (lr.nextDouble() - 0.5) * radius;
                    double px = x - Math.cos(angle) * offset;
                    double py = y - Math.sin(angle) * offset;

                    double ex = px - Math.cos(angle) * len;
                    double ey = py - Math.sin(angle) * len;

                    g.strokeLine(px, py, ex, ey);
                }

                g.restore();
            }
            super.draw(g);
        }
        @Override String getStatsText() { double sp=Math.sqrt(vx*vx+vy*vy); return String.format("⚡ VEL:%.1f DMG:%.0f",sp,damage); }
    }

    // ── 5. Adaga ─────────────────────────────────────────────────────────
    class DaggerBall extends Ball {
        double ang=0, spin=15, len=65, dDmg=1;
        DaggerBall(double x, double y) { super(x,y,Color.GOLD); damage=0; attackCooldownFrames=5; }
        void angle(double f) { ang+=spin*f; }
        @Override void onHit(Ball t) {}
        void daggerCheck(Ball t) {
            double r=Math.toRadians(ang),tx=x+Math.cos(r)*(radius+len),ty=y+Math.sin(r)*(radius+len);
            double dx=tx-t.x,dy=ty-t.y;
            if(dx*dx+dy*dy<(t.radius+5)*(t.radius+5) && hitCooldownFrames<=0){
                t.lastAttacker=this; t.takeDamage(dDmg);
                spawnFloat(t.x,t.y-28,"-"+(int)dDmg,tColor(teamId));
                spin=Math.min(spin+1,140); hitCooldownFrames=attackCooldownFrames;
            }
        }
        @Override void draw(GraphicsContext g) {
            super.draw(g); g.save(); g.translate(x,y); g.rotate(ang);
            g.setFill(Color.SILVER); g.beginPath(); g.moveTo(radius,-4); g.lineTo(radius+len,0); g.lineTo(radius,4); g.fill();
            g.setFill(Color.web("#8B4513")); g.fillRect(radius-5,-3,7,6); g.restore();
        }
        @Override String getStatsText() { return String.format("🔪 DMG:%.0f SPD:%.0f°",dDmg,spin); }
    }

    // ── 6. Centy ─────────────────────────────────────────────────────────
    class Centy extends Ball {
        Centy(double x, double y) { super(x,y,Color.web("#DDDDDD")); }
        @Override void onHit(Ball t) { double d=t.hp>1?t.maxHp*(damage/100.0):1; t.takeDamage(d); damage=Math.min(damage+1,99); }
        @Override String getStatsText() { return String.format("☠ %%HP: %.0f%%",damage); }
    }

    // ── 7. Tanque ─────────────────────────────────────────────────────────
    class TankBall extends Ball {
        double armor=0.35;
        TankBall(double x, double y) { super(x,y,Color.STEELBLUE); mass=3.0; damage=1; radius=DEFAULT_RADIUS*1.15; }
        @Override void takeDamage(double d) { super.takeDamage(d*(1-armor)); }
        @Override void applyMode(double m) { maxHp*=m; }
        @Override void onHit(Ball t) { t.takeDamage(damage); damage+=1; }
        @Override void draw(GraphicsContext g) {
            super.draw(g);
            // Armor rings
            g.save(); g.setGlobalAlpha(0.20); g.setStroke(Color.LIGHTSTEELBLUE); g.setLineWidth(3);
            g.strokeOval(x-radius-5,y-radius-5,(radius+5)*2,(radius+5)*2);
            g.setGlobalAlpha(0.10); g.strokeOval(x-radius-10,y-radius-10,(radius+10)*2,(radius+10)*2);
            g.restore();
        }
        @Override String getStatsText() { return String.format("🛡 DMG:%.0f ARM:%.0f%%",damage,armor*100); }
    }

    // ── 8. Vampiro ────────────────────────────────────────────────────────
    class VampireBall extends Ball {
        VampireBall(double x, double y) { super(x,y,Color.MEDIUMPURPLE); }
        @Override void onHit(Ball t) { t.takeDamage(damage); }
        void vampHeal(double amt) { hp=Math.min(hp+amt,maxHp); healFlashFrames=8; damage+=0.5; spawnFloat(x,y-22,"+"+String.format("%.0f",amt),Color.LIMEGREEN); }
        @Override String getStatsText() { return String.format("🧛 DMG:%.0f HP:%.0f",damage,hp); }
    }

    // ── 9. Bomba ─────────────────────────────────────────────────────────
    class BombBall extends Ball {
        int timer=180; double expR=DEFAULT_RADIUS*2.5, bDmg=1;
        BombBall(double x, double y) { super(x,y,Color.DARKORANGE); mass=1.3; }
        @Override void onHit(Ball t) { t.takeDamage(damage); }
        @Override void onFrame(List<Ball> all) {
            timer--;
            if(timer<=0){
                timer=180;
                for(Ball t:all){
                    if(t!=this&&t.teamId!=teamId){
                        double dx=t.x-x,dy=t.y-y,d=Math.sqrt(dx*dx+dy*dy);
                        if(d<expR+t.radius){ t.lastAttacker=this; double dealt=bDmg*(1-d/expR*0.5); t.takeDamage(Math.max(1,dealt)); spawnFloat(t.x,t.y-22,"-"+(int)dealt,tColor(teamId)); }
                    }
                }
                explosions.add(new Explosion(x,y,expR,Color.ORANGE));
                bDmg += 1 + (bDmg * 0.1); expR=Math.min(expR+DEFAULT_RADIUS*0.1,DEFAULT_RADIUS*2);
            }
        }
        @Override void draw(GraphicsContext g) {
            // Fuse indicator
            double pct=1.0-(double)timer/180;
            g.save(); g.setGlobalAlpha(0.25+pct*0.40); g.setFill(Color.RED);
            g.fillOval(x-radius-3*pct*6,y-radius-3*pct*6,(radius+3*pct*6)*2,(radius+3*pct*6)*2); g.restore();
            super.draw(g);
            // Fuse line
            g.save(); g.setStroke(Color.YELLOW); g.setLineWidth(2);
            g.strokeLine(x,y-radius,x,y-radius-12*(1-pct)); g.restore();
        }
        @Override String getStatsText() { return String.format("💣 Dano Explosão:%.0f Alcance Explosão:%.0f T:%d",bDmg,expR,timer); }
    }

    // ── 10. Espinho ───────────────────────────────────────────────────────
    class ThornBall extends Ball {
        double reflRatio=0.40;
        ThornBall(double x, double y) { super(x,y,Color.web("#4CAF50")); }
        @Override void takeDamage(double d) {
            super.takeDamage(d);
            if(lastAttacker!=null&&lastAttacker.hp>0){
                double ref=d*reflRatio; lastAttacker.hp-=ref; lastAttacker.damageFlashFrames=FLASH;
                spawnFloat(lastAttacker.x,lastAttacker.y-22,"-"+(int)ref+"↩",tColor(teamId));
                reflRatio=Math.min(reflRatio+0.01,0.90);
            }
        }
        @Override void onHit(Ball t) { t.takeDamage(damage); }
        @Override void draw(GraphicsContext g) {
            super.draw(g);
            // Thorn spikes
            g.save(); g.setStroke(Color.web("#2E7D32")); g.setLineWidth(2.5);
            for(int i=0;i<8;i++){
                double a=Math.toRadians(i*45),sx=x+Math.cos(a)*(radius-2),sy=y+Math.sin(a)*(radius-2);
                double ex=x+Math.cos(a)*(radius+10),ey=y+Math.sin(a)*(radius+10);
                g.strokeLine(sx,sy,ex,ey);
            }
            g.restore();
        }
        @Override String getStatsText() { return String.format("🌵 RFLT:%.0f%%",reflRatio*100); }
    }
}