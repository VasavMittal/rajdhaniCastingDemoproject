package com.ranjdhaniCastingDemoproject.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    // NOTE: root remains the BorderPane used for main layout.
    private BorderPane root;

    // overlay stack that hosts root + floating truck box (prevents extra vertical gap)
    private StackPane overlay;

    // Left (price stack top-left + main weight)
    private VBox leftContainer;
    private VBox priceStack;      // Price1/Price2 small boxes (top-left)
    private TextField price1Field;
    private TextField price2Field;
    private TextField mainWeightField;

    private HBox bottomButtons;
    private javafx.scene.control.Button printButton;
    private javafx.scene.control.Button resetButton;


    // Top-right truck number (fixed)
    private TextField truckNumberField;

    // Right: subweights area and totals area (wrapped inside ScrollPane)
    private VBox rightContainer;
    private ScrollPane rightScrollPane;
    private VBox swArea;
    private VBox totalsArea;

    private TextField gstField;

    // Live SWs (created while user enters)
    private final List<TextField> swLive = new ArrayList<>();

    // After finish: lists for sw (read-only), price results and quality fields
    private final List<TextField> swFields = new ArrayList<>();
    private final List<TextField> priceFields = new ArrayList<>();
    private final List<TextField> qualityFields = new ArrayList<>();
    private ComboBox<String> dustDiscountBox; // reference to dust combo box

    private boolean subweightsFinished = false;

    private DoubleBinding fontSize;
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    @Override
    public void start(Stage stage) {
        // Create main BorderPane layout
        root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // Create overlay StackPane that will host the BorderPane and an overlaid truck box.
        overlay = new StackPane();
        overlay.getChildren().add(root);

        Scene scene = new Scene(overlay, 1600, 900);
        fontSize = scene.heightProperty().divide(30);

        buildLeftContainer();   // creates priceStack + mainWeightField
        buildRightContainer(scene);
        buildTopRightTruckField(); // creates truckNumberField in top-right (OVERLAY)
        buildBottomButtons();
        bottomButtons.setVisible(false);
        bottomButtons.setManaged(false);


        Platform.runLater(() -> {
            truckNumberField.requestFocus();
            truckNumberField.positionCaret(truckNumberField.getText().length());
        });

        // listeners to recompute when base prices change
        price1Field.textProperty().addListener((o, ov, nv) -> recomputeAllRows());
        price2Field.textProperty().addListener((o, ov, nv) -> recomputeAllRows());

        // Key handling (ENTER / F1 reset / F4 exit)
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.F1) {
                resetAll(stage);
                ev.consume();
                return;
            }
            if (ev.getCode() == KeyCode.F4) {
                Platform.exit();
                return;
            }
            if (ev.getCode() == KeyCode.ENTER) {
                handleEnter(stage);
                ev.consume();
            }
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setTitle("Rajdhani Casting Demo");
        stage.show();
    }

    private void buildBottomButtons() {

        printButton = new javafx.scene.control.Button("PRINT");
        resetButton = new javafx.scene.control.Button("RESET");

        printButton.setPrefWidth(200);
        resetButton.setPrefWidth(200);

        printButton.setStyle(
                "-fx-font-size: 24px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 3; " +
                "-fx-background-color: white;"
        );

        resetButton.setStyle(
                "-fx-font-size: 24px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 3; " +
                "-fx-background-color: white;"
        );

        // When PRINT has focus → blue border + light-blue background
        printButton.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV)
                printButton.setStyle("-fx-font-size: 24px; -fx-border-color: #007BFF; -fx-border-width: 4; -fx-background-color: #D6E9FF;");
            else
                printButton.setStyle("-fx-font-size: 24px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        });

        // When RESET has focus → red border + light-red background
        resetButton.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV)
                resetButton.setStyle("-fx-font-size: 24px; -fx-border-color: #FF3B3B; -fx-border-width: 4; -fx-background-color: #FFD6D6;");
            else
                resetButton.setStyle("-fx-font-size: 24px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        });


        printButton.setFocusTraversable(true);
        resetButton.setFocusTraversable(true);

        // ENTER actions
        printButton.setOnAction(e -> printSlip());
        resetButton.setOnAction(e -> resetAll((Stage) overlay.getScene().getWindow()));

        // Arrow key navigation
        printButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.RIGHT) resetButton.requestFocus();
        });

        resetButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.LEFT) printButton.requestFocus();
        });

        bottomButtons = new HBox(20);
        bottomButtons.setAlignment(Pos.BOTTOM_RIGHT);
        bottomButtons.setPadding(new Insets(0, 40, 30, 0));
        bottomButtons.getChildren().addAll(printButton, resetButton);

        bottomButtons.setVisible(false);
        bottomButtons.setManaged(false);

        overlay.getChildren().add(bottomButtons);
        StackPane.setAlignment(bottomButtons, Pos.BOTTOM_RIGHT);
    }

    private void printSlip() {
        System.out.println("PRINT clicked — integrate printer later.");
    }



    // ---------------- Build top-right truck field (fixed, overlaid so it doesn't add height) ----------------
    private void buildTopRightTruckField() {
        truckNumberField = new TextField();
        truckNumberField.setPromptText("Truck Number");
        truckNumberField.setPrefWidth(260);
        truckNumberField.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(fontSize.get()), fontSize));
        truckNumberField.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");

        // Container to host the truck field but NOT affect the BorderPane layout
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(8, 24, 0, 0)); // minimal padding; adjust if needed
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.getChildren().add(truckNumberField);

        // Add the topBar to overlay (on top of the BorderPane)
        overlay.getChildren().add(topBar);
        StackPane.setAlignment(topBar, Pos.TOP_RIGHT);
        StackPane.setMargin(topBar, new Insets(8, 24, 0, 0));
    }

    // ---------------- Build left container (price stack above main weight) ----------------
    private void buildLeftContainer() {
        leftContainer = new VBox();
        leftContainer.setAlignment(Pos.TOP_LEFT);
        leftContainer.setPadding(new Insets(20));
        leftContainer.setSpacing(12);
        leftContainer.setPrefWidth(520);

        // Price stack (top-left) — VISIBLE from start but DISABLED until SW finished
        priceStack = new VBox(8);
        priceStack.setAlignment(Pos.TOP_LEFT);
        price1Field = makeField("Price 1 (per ton)", 220);
        price2Field = makeField("Price 2 (per ton)", 220);
        priceStack.getChildren().addAll(price1Field, price2Field);

        // show priceStack visually, but disable editing until subweights finish
        priceStack.setVisible(true);
        priceStack.setManaged(true);
        price1Field.setDisable(true);
        price2Field.setDisable(true);

        // Spacer pushes main weight down to center-left area
        Region spacer = new Region();
        spacer.setMinHeight(0);
        spacer.prefHeightProperty().bind(root.heightProperty().multiply(0.15)); // tweak multiplier if needed

        mainWeightField = makeField("Main Weight (kg)", 440);

        leftContainer.getChildren().addAll(priceStack, spacer, mainWeightField);
        root.setLeft(leftContainer);
        BorderPane.setAlignment(leftContainer, Pos.CENTER_LEFT);
    }

    // ---------------- Build right container (subweights area + totals) ----------------
    private void buildRightContainer(Scene scene) {
        rightContainer = new VBox(12);
        rightContainer.setPadding(new Insets(12, 60, 40, 40));
        rightContainer.setAlignment(Pos.TOP_LEFT);

        // spacer to vertically align SWs near center-left
        Region spacer = new Region();
        spacer.prefHeightProperty().bind(scene.heightProperty().multiply(0.22));
        rightContainer.getChildren().add(spacer);

        // swArea (live inputs initially)
        swArea = new VBox(12);
        swArea.setAlignment(Pos.TOP_LEFT);
        addSwLiveField(); // first live SW

        // totals area hidden initially
        totalsArea = new VBox(12);
        totalsArea.setAlignment(Pos.CENTER);
        totalsArea.setVisible(false);
        totalsArea.setManaged(false);

        rightContainer.getChildren().addAll(swArea, totalsArea);

        // Wrap the rightContainer inside a ScrollPane so many SW rows won't break layout
        rightScrollPane = new ScrollPane(rightContainer);
        rightScrollPane.setFitToWidth(true);
        rightScrollPane.setFitToHeight(true);
        rightScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        rightScrollPane.setStyle("-fx-background: white; -fx-background-color: white;");
        rightContainer.setStyle("-fx-background-color: white;");

        rightScrollPane.setFocusTraversable(false);

        root.setCenter(rightScrollPane);
    }

    // ---------------- UI helpers ----------------
    private TextField makeField(String prompt, double width) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(width);
        tf.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        tf.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        return tf;
    }

    private TextField makeReadOnly(double width, String text) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.setPrefWidth(width);
        tf.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        tf.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        return tf;
    }

    private TextField makePriceResult(double width) {
        TextField tf = new TextField();
        tf.setEditable(false);
        tf.setPrefWidth(width);
        tf.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get() * 0.85), fontSize));
        tf.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        tf.setText("");
        tf.setVisible(true);
        tf.setManaged(true);
        return tf;
    }

    private TextField makeQualityField(double width) {
        TextField tf = new TextField();
        tf.setPrefWidth(width);
        tf.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get() * 0.85), fontSize));
        tf.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");
        return tf;
    }

    private Label makeMultiplyLabel() {
        Label l = new Label("×");
        l.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get() * 0.9), fontSize));
        l.setMinWidth(30);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    // ---------------- Live SW entry ----------------
    private void addSwLiveField() {
        TextField sw = makeField("", 360);
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(sw);

        swArea.getChildren().add(row);
        swLive.add(sw);
        sw.requestFocus();
    }

    // ---------------- ENTER flow ----------------
    private void handleEnter(Stage stage) {
        Object focused = stage.getScene().getFocusOwner();

        // If focus on truck number -> go to main weight
        if (focused == truckNumberField) {
            mainWeightField.requestFocus();
            return;
        }

        // If focus on main weight -> go to first SW
        if (focused == mainWeightField) {
            if (!swLive.isEmpty()) swLive.get(0).requestFocus();
            return;
        }

        // If still live SW mode
        if (!subweightsFinished) {
            for (int i = 0; i < swLive.size(); i++) {
                TextField sw = swLive.get(i);
                if (focused == sw) {
                    String t = sw.getText().trim();
                    if (t.equals("0") || t.equals("0.0") || t.equals("0.00")) {
                        finishSubweightsFromLive(i);
                        return;
                    }
                    // else create next live SW if last
                    if (i == swLive.size() - 1) addSwLiveField();
                    else swLive.get(i + 1).requestFocus();
                    return;
                }
            }
        }

        // After SW finished: Price1 -> Price2 -> first quality-like field
        if (focused == price1Field) {
            if (!price2Field.isDisabled()) price2Field.requestFocus();
            return;
        }
        if (focused == price2Field) {
            for (TextField q : qualityFields) {
                if (q != null && q.isVisible()) { q.requestFocus(); return; }
            }
            return;
        }

        // Quality fields: compute row price and advance
        for (int i = 0; i < qualityFields.size(); i++) {
            TextField q = qualityFields.get(i);
            if (q == null) continue;
            if (focused == q) {
                computeRowPrice(i);
                int next = nextVisibleQuality(i);
                if (next >= 0) qualityFields.get(next).requestFocus();
                else showTotals();
                return;
            }
        }

        // If focus in GST -> apply
        if (focused == gstField) {
            applyGst();

            // SHOW BOTTOM BUTTONS
            bottomButtons.setVisible(true);
            bottomButtons.setManaged(true);

            // Focus goes to PRINT button
            Platform.runLater(() -> printButton.requestFocus());
        }
    }

    private int nextVisibleQuality(int from) {
        for (int j = from + 1; j < qualityFields.size(); j++) {
            TextField tf = qualityFields.get(j);
            if (tf != null && tf.isVisible()) return j;
        }
        return -1;
    }

    // ---------------- Finish SWs when sentinel entered during live entry ----------------
    private void finishSubweightsFromLive(int sentinelIndex) {
        // Build raw list from swLive[0..sentinelIndex-1]
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < sentinelIndex; i++) raw.add(swLive.get(i).getText().trim());
        finishSubweightsFromRaw(raw);
    }

    // Build entries from a raw list of string entries where last non-empty is user-entered dust.
    private void finishSubweightsFromRaw(List<String> raw) {
        subweightsFinished = true;

        // Convert raw to entries (Double or null)
        List<Double> entries = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) entries.add(null);
            else {
                try { entries.add(Double.parseDouble(s.replace(",", ""))); }
                catch (Exception ex) { entries.add(null); }
            }
        }

        // find last non-null index (dust)
        int lastNonNullIndex = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) != null) { lastNonNullIndex = i; break; }
        }

        double mainKg = safeParse(mainWeightField.getText());

        // If nothing entered -> single dust = mainKg
        if (entries.isEmpty() || lastNonNullIndex == -1) {
            swArea.getChildren().clear();
            swFields.clear(); priceFields.clear(); qualityFields.clear();

            TextField dust = makeReadOnly(360, moneyFmt.format(mainKg));
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(dust);
            swArea.getChildren().add(row);

            swFields.add(dust);
            priceFields.add(makePriceResult(220)); priceFields.get(0).setVisible(false); priceFields.get(0).setManaged(false);
            qualityFields.add(makeQualityField(180)); qualityFields.get(0).setVisible(false); qualityFields.get(0).setManaged(false);

            // enable price editing (there's nothing else but we let user edit prices now)
            price1Field.setDisable(false);
            price2Field.setDisable(false);
            priceStack.setVisible(true);
            priceStack.setManaged(true);
            price1Field.requestFocus();
            return;
        }

        // dust value
        double dustValue = entries.get(lastNonNullIndex);

        // preDust entries
        List<Double> preDust = new ArrayList<>();
        for (int i = 0; i < lastNonNullIndex; i++) preDust.add(entries.get(i));

        // compute sumPre and find first empty
        double sumPre = 0;
        int firstEmptyIndex = -1;
        for (int i = 0; i < preDust.size(); i++) {
            Double v = preDust.get(i);
            if (v == null) { if (firstEmptyIndex == -1) firstEmptyIndex = i; }
            else sumPre += v;
        }

        double sumWithDust = sumPre + dustValue;
        double remaining = mainKg - sumWithDust;
        if (remaining < 0) remaining = 0;

        if (firstEmptyIndex != -1) {
            preDust.set(firstEmptyIndex, remaining);
        } else {
            if (!preDust.isEmpty()) {
                Double v0 = preDust.get(0);
                if (v0 == null) v0 = 0.0;
                preDust.set(0, v0 + remaining);
            } else {
                preDust.add(remaining);
            }
        }

        // finalSWs = preDust + dust
        List<Double> finalSWs = new ArrayList<>();
        for (Double d : preDust) finalSWs.add(d == null ? 0.0 : d);
        finalSWs.add(dustValue);

        // build rows: for each finalSW except last (dust) show SW | × | Price (visible read-only) | Quality
        swArea.getChildren().clear();
        swFields.clear(); priceFields.clear(); qualityFields.clear();

        for (int i = 0; i < finalSWs.size(); i++) {
            double val = finalSWs.get(i);
            boolean isDust = (i == finalSWs.size() - 1);

            TextField swR = makeReadOnly(360, moneyFmt.format(val));
            Label mul = makeMultiplyLabel();
            TextField priceRes = makePriceResult(220);
            // priceRes is visible (read-only) by default now
            priceRes.setVisible(true);
            priceRes.setManaged(true);
            TextField qual = makeQualityField(180);

            if (isDust) {

                // Placeholder for ×
                Region mulEmpty = new Region();
                mulEmpty.setPrefWidth(30);

                // Placeholder for price field
                Region priceEmpty = new Region();
                priceEmpty.setPrefWidth(220);

                // Editable ComboBox inside QUALITY column
                ComboBox<String> discount = new ComboBox<>();
                discount.setEditable(true);
                discount.getItems().addAll("1.5", "1", "N");
                discount.setValue("N");
                discount.setPrefWidth(180);
                discount.setStyle("-fx-border-color: black; -fx-border-width: 3;");
                discount.getEditor().setFont(Font.font(fontSize.get() * 0.85));

                // -------------------------------
                // 1️⃣ Auto-open dropdown on focus
                // -------------------------------
                discount.focusedProperty().addListener((obs, oldV, newV) -> {
                    if (newV) {
                        Platform.runLater(discount::show);  // automatically open popup
                    }
                });

                // ---------------------------------------
                // 2️⃣ Support keyboard navigation + ENTER
                // ---------------------------------------
                discount.setOnKeyPressed(ev -> {
                    switch (ev.getCode()) {

                        case DOWN:   // show popup & navigate
                            discount.show();
                            break;

                        case UP:
                            discount.show();
                            break;

                        case ENTER:
                            // commit typed value
                            String entered = discount.getEditor().getText();
                            if (!discount.getItems().contains(entered)) {
                                discount.setValue(entered);
                            }
                            showTotals();     // proceed to totals
                            ev.consume();
                            break;
                    }
                });

                // If cursor is inside editor (typing)
                discount.getEditor().setOnKeyPressed(ev -> {
                    if (ev.getCode() == KeyCode.ENTER) {
                        String entered = discount.getEditor().getText();
                        if (!discount.getItems().contains(entered)) {
                            discount.setValue(entered);
                        }
                        showTotals();
                        ev.consume();
                    }
                });

                // ---------------------------------------
                // 3️⃣ Update totals while typing/selecting
                // ---------------------------------------
                discount.valueProperty().addListener((o, ov, nv) -> updateTotalsIfVisible());
                discount.getEditor().textProperty().addListener((o, ov, nv) -> updateTotalsIfVisible());

                // -------------------------------
                // Build dust row
                // -------------------------------
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(swR, mulEmpty, priceEmpty, discount);
                swArea.getChildren().add(row);

                // Register fields in lists correctly
                swFields.add(swR);
                priceFields.add(null);                   // dust has NO price field
                qualityFields.add(discount.getEditor()); // discount acts as a quality field

                dustDiscountBox = discount;

                continue;    // skip normal block
            }
            else {
                // normal row
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(swR, mul, priceRes, qual);
                swArea.getChildren().add(row);

                swFields.add(swR);
                priceFields.add(priceRes);
                qualityFields.add(qual);
            }
        }

        // Enable price editing now that SWs finished (option B)
        price1Field.setDisable(false);
        price2Field.setDisable(false);
        price1Field.requestFocus();

        // Ensure totals area will be available when needed
        totalsArea.setVisible(false);
        totalsArea.setManaged(false);
    }

    // ---------------- Compute row price ----------------
    private void computeRowPrice(int index) {
        if (index < 0 || index >= priceFields.size()) return;

        TextField sw = swFields.get(index);
        TextField priceOut = priceFields.get(index);
        TextField q = qualityFields.get(index);

        double swKg = safeParse(sw.getText());
        if (swKg <= 0) return;
        if (priceOut == null) return; // safety for dust row

        double p1 = safeParse(price1Field.getText());
        double p2 = safeParse(price2Field.getText());
        double qv = safeParse(q.getText());

        // Row "price field" shows only raw rate (p1+p2+qv)
        double rowDisplayRate = p1 + p2 + qv;
        priceOut.setText(moneyFmt.format(rowDisplayRate));   // SHOW ONLY RATE

        // Store the ACTUAL price (rate × weight) inside TextField `priceOut` userData
        double actualPrice = rowDisplayRate * (swKg / 1000.0);
        priceOut.setUserData(actualPrice);

        // Update totals
        updateTotalsIfVisible();
    }

    // recompute all rows when base prices change
    private void recomputeAllRows() {
        for (int i = 0; i < priceFields.size(); i++) {
            TextField pf = priceFields.get(i);
            TextField q = null;
            if (i < qualityFields.size()) q = qualityFields.get(i);
            if (pf != null && q != null && q.isVisible()) {
                computeRowPrice(i);
            }
        }
    }

    // ---------------- Totals, GST ----------------
    private void showTotals() {
        updateTotalsIfVisible();

        totalsArea.getChildren().clear();

        // ---- Top black line ----
        Region topLine = new Region();
        topLine.setPrefHeight(6);
        topLine.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        topLine.setMaxWidth(Double.MAX_VALUE);

        // ---- Total value (center) ----
        Label totalVal = new Label(totalsFormatted());
        totalVal.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        totalVal.setAlignment(Pos.CENTER);

        // ---- GST input (center) ----
        gstField = new TextField();
        gstField.setPromptText("GST Amount (flat)");
        gstField.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get() * 0.9), fontSize));
        gstField.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-background-color: white;");
        gstField.setAlignment(Pos.CENTER);  // <<<<<< CENTER TEXT INSIDE BOX
        gstField.setMaxWidth(260);          // <<<<<< CENTERED INPUT FIELD

        VBox centerBox = new VBox(10);
        centerBox.setAlignment(Pos.CENTER); // <<<<<< THIS CENTERS GST UNDER TOTAL
        centerBox.getChildren().addAll(totalVal, gstField);

        // ---- Bottom black line ----
        Region bottomLine = new Region();
        bottomLine.setPrefHeight(6);
        bottomLine.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        bottomLine.setMaxWidth(Double.MAX_VALUE);

        // ---- Final total (center) ----
        Label finalVal = new Label(totalsFormatted());
        finalVal.setId("finalValue");
        finalVal.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        finalVal.setAlignment(Pos.CENTER);

        // Add everything in correct order
        totalsArea.getChildren().addAll(topLine, centerBox, bottomLine, finalVal);
        totalsArea.setVisible(true);
        totalsArea.setManaged(true);

        gstField.requestFocus();
    }

    private void applyGst() {
        double gst = safeParse(gstField.getText());
        double total = totalsValue();
        double finalV = total + gst;

        Label lbl = (Label) totalsArea.lookup("#finalValue");
        if (lbl != null) lbl.setText(moneyFmt.format(finalV));
    }

    private void updateTotalsIfVisible() {
        if (!totalsArea.isVisible()) return;
        Label totalVal = (Label) ((VBox) totalsArea.getChildren().get(1)).getChildren().get(0);
        totalVal.setText(totalsFormatted());
        Label finalVal = (Label) totalsArea.getChildren().get(3);
        finalVal.setText(totalsFormatted());
    }

    private double totalsValue() {
        double total = 0;

        // calculate row totals (rate × weight) using stored userData
        for (int i = 0; i < priceFields.size(); i++) {
            TextField pf = priceFields.get(i);
            if (pf == null || !pf.isVisible()) continue;

            Object ud = pf.getUserData();
            if (ud instanceof Double) {
                total += (Double) ud;
            }
        }

        // ---- Apply dust discount (percentage of total) ----
        if (dustDiscountBox != null) {
            String v = dustDiscountBox.getValue();
            double pct = 0;

            if ("1.5".equals(v)) pct = 1.5;
            else if ("1".equals(v)) pct = 1;

            double discount = total * (pct / 100.0);
            total -= discount;
        }

        return total;
    }

    private String totalsFormatted() {
        return moneyFmt.format(totalsValue());
    }

    // ---------------- Utilities ----------------
    private double safeParse(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return 0; }
    }

    // ---------------- Reset ----------------
    private void resetAll(Stage stage) {
        subweightsFinished = false;
        swLive.clear();
        swFields.clear();
        priceFields.clear();
        qualityFields.clear();
        dustDiscountBox = null;

        // clear truck number too
        if (truckNumberField != null) truckNumberField.clear();

        if (bottomButtons != null) {
            bottomButtons.setVisible(false);
            bottomButtons.setManaged(false);
        }
        // rebuild left container (keep priceStack in left) — price fields disabled again
        root.setLeft(null);
        buildLeftContainer();
        Platform.runLater(() -> truckNumberField.requestFocus());


        // rebuild right container and scrollpane
        rightContainer.getChildren().clear();
        Region spacer = new Region();
        spacer.prefHeightProperty().bind(stage.getScene().heightProperty().multiply(0.22));
        rightContainer.getChildren().add(spacer);

        swArea = new VBox(12);
        swArea.setAlignment(Pos.TOP_LEFT);
        addSwLiveField();

        totalsArea = new VBox(12);
        totalsArea.setAlignment(Pos.CENTER);
        totalsArea.setVisible(false);
        totalsArea.setManaged(false);

        rightContainer.getChildren().addAll(swArea, totalsArea);

        // rewrap in scrollpane and set center
        rightScrollPane = new ScrollPane(rightContainer);
        rightScrollPane.setFitToWidth(true);
        rightScrollPane.setFitToHeight(true);
        rightScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        rightScrollPane.setStyle("-fx-background: white; -fx-background-color: white;");
        rightContainer.setStyle("-fx-background-color: white;");

        root.setCenter(rightScrollPane);

        mainWeightField.clear();

        // force focus AFTER UI fully rebuilds
        Platform.runLater(() -> {
            if (truckNumberField != null) {
                truckNumberField.requestFocus();
                truckNumberField.positionCaret(truckNumberField.getText().length());
            }
        });

    }

    public static void main(String[] args) {
        launch();
    }
}
