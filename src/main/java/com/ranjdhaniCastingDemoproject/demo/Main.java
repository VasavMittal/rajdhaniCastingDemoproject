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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.printing.PDFPrintable;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    private BorderPane root;
    private StackPane overlay;

    // Left (price stack top-left + main weight)
    private VBox leftContainer;
    private VBox priceStack;
    private TextField price1Field;
    private TextField price2Field;
    private TextField mainWeightField;

    // Buttons (kept in overlay)
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
    private StackPane loaderOverlay;
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
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0");  // Remove .00

    @Override
    public void start(Stage stage) {
        // Create main BorderPane layout
        root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // Overlay hosts root + floating elements (truck box and bottom buttons)
        overlay = new StackPane();
        overlay.getChildren().add(root);

        Scene scene = new Scene(overlay, 1600, 900);
        fontSize = scene.heightProperty().divide(30);

        buildLeftContainer();
        buildRightContainer(scene);
        buildTopRightTruckField();
        buildBottomButtons();
        buildLoader();

        bottomButtons.setVisible(false);
        bottomButtons.setManaged(false);

        // ensure truck number is focused at start
        Platform.runLater(() -> {
            if (truckNumberField != null) truckNumberField.requestFocus();
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

    private void buildLoader() {
        loaderOverlay = new StackPane();
        loaderOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.4);");
        loaderOverlay.setVisible(false);
        loaderOverlay.setManaged(false);

        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(120, 120);

        loaderOverlay.getChildren().add(pi);
        StackPane.setAlignment(pi, Pos.CENTER);

        overlay.getChildren().add(loaderOverlay);
    }


    // ---------------- Build top-right truck field ----------------
    private void buildTopRightTruckField() {
        truckNumberField = new TextField();
        truckNumberField.setPromptText("Truck Number");
        truckNumberField.setPrefWidth(260);
        truckNumberField.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(fontSize.get()), fontSize));
        truckNumberField.setStyle("-fx-border-color: black; -fx-border-width: 3; -fx-background-color: white;");

        HBox topBar = new HBox();
        topBar.setPadding(new Insets(8, 24, 0, 0));
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.getChildren().add(truckNumberField);

        overlay.getChildren().add(topBar);
        StackPane.setAlignment(topBar, Pos.TOP_RIGHT);
        StackPane.setMargin(topBar, new Insets(8, 24, 0, 0));
    }

    // ---------------- Build left container ----------------
    private void buildLeftContainer() {
        leftContainer = new VBox();
        leftContainer.setAlignment(Pos.TOP_LEFT);
        leftContainer.setPadding(new Insets(20));
        leftContainer.setSpacing(12);
        leftContainer.setPrefWidth(520);

        priceStack = new VBox(8);
        priceStack.setAlignment(Pos.TOP_LEFT);
        price1Field = makeField("Price 1 (per ton)", 220);
        price2Field = makeField("Price 2 (per ton)", 220);
        priceStack.getChildren().addAll(price1Field, price2Field);

        priceStack.setVisible(true);
        priceStack.setManaged(true);
        price1Field.setDisable(true);
        price2Field.setDisable(true);

        Region spacer = new Region();
        spacer.setMinHeight(0);
        spacer.prefHeightProperty().bind(root.heightProperty().multiply(0.15));

        mainWeightField = makeField("Main Weight (kg)", 440);

        leftContainer.getChildren().addAll(priceStack, spacer, mainWeightField);
        root.setLeft(leftContainer);
        BorderPane.setAlignment(leftContainer, Pos.CENTER_LEFT);
    }

    // ---------------- Build right container ----------------
    private void buildRightContainer(Scene scene) {
        rightContainer = new VBox(12);
        rightContainer.setPadding(new Insets(12, 60, 40, 40));
        rightContainer.setAlignment(Pos.TOP_LEFT);

        Region spacer = new Region();
        spacer.prefHeightProperty().bind(scene.heightProperty().multiply(0.22));
        rightContainer.getChildren().add(spacer);

        swArea = new VBox(12);
        swArea.setAlignment(Pos.TOP_LEFT);
        addSwLiveField();

        totalsArea = new VBox(12);
        totalsArea.setAlignment(Pos.CENTER);
        totalsArea.setVisible(false);
        totalsArea.setManaged(false);

        rightContainer.getChildren().addAll(swArea, totalsArea);

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

    // ---------------- Build bottom buttons ----------------
    private void buildBottomButtons() {
        printButton = new javafx.scene.control.Button("PRINT");
        resetButton = new javafx.scene.control.Button("RESET");
        printButton.setPrefWidth(200);
        resetButton.setPrefWidth(200);

        // Simple focus style that makes selected button visible
        printButton.setStyle(
            "-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3;" +
            "-fx-background-color: #e3e3e3;" // default
        );

        resetButton.setStyle(
            "-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3;" +
            "-fx-background-color: #e3e3e3;"
        );

        printButton.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV)
                printButton.setStyle("-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: yellow;");
            else
                printButton.setStyle("-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: #e3e3e3;");
        });

        resetButton.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV)
                resetButton.setStyle("-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: yellow;");
            else
                resetButton.setStyle("-fx-font-size: 18px; -fx-border-color: black; -fx-border-width: 3; -fx-background-color: #e3e3e3;");
        });


        printButton.setFocusTraversable(true);
        resetButton.setFocusTraversable(true);

        // Arrow key navigation
        printButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.RIGHT) resetButton.requestFocus();
        });
        resetButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.LEFT) printButton.requestFocus();
        });

        printButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                startPrintWithLoader();
                ev.consume();
            }
        });

        resetButton.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                resetAll((Stage) overlay.getScene().getWindow());
                ev.consume();
            }
        });

        bottomButtons = new HBox(20);
        bottomButtons.setAlignment(Pos.BOTTOM_RIGHT);
        bottomButtons.setPadding(new Insets(0, 40, 30, 0));
        bottomButtons.getChildren().addAll(printButton, resetButton);

        overlay.getChildren().add(bottomButtons);
        StackPane.setAlignment(bottomButtons, Pos.BOTTOM_RIGHT);
    }

    private void startPrintWithLoader() {
        loaderOverlay.setVisible(true);
        loaderOverlay.setManaged(true);

        new Thread(() -> {
            try {
                printSlip();   // heavy work (PDF generation)
            } finally {
                Platform.runLater(() -> {
                    loaderOverlay.setVisible(false);
                    loaderOverlay.setManaged(false);
                    resetAll((Stage) overlay.getScene().getWindow());
                });
            }
        }).start();
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

        if (focused == truckNumberField) {
            mainWeightField.requestFocus();
            return;
        }

        if (focused == mainWeightField) {
            if (!swLive.isEmpty()) swLive.get(0).requestFocus();
            return;
        }

        if (!subweightsFinished) {
            for (int i = 0; i < swLive.size(); i++) {
                TextField sw = swLive.get(i);
                if (focused == sw) {
                    String t = sw.getText().trim();
                    if (t.equals("0.0") || t.equals("0.00")) {
                        finishSubweightsFromLive(i);
                        return;
                    }
                    if (i == swLive.size() - 1) addSwLiveField();
                    else swLive.get(i + 1).requestFocus();
                    return;
                }
            }
        }

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
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < sentinelIndex; i++) raw.add(swLive.get(i).getText().trim());
        finishSubweightsFromRaw(raw);
    }

    private void finishSubweightsFromRaw(List<String> raw) {
        subweightsFinished = true;

        List<Double> entries = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) entries.add(null);
            else {
                try { entries.add(Double.parseDouble(s.replace(",", ""))); }
                catch (Exception ex) { entries.add(null); }
            }
        }

        int lastNonNullIndex = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) != null) { lastNonNullIndex = i; break; }
        }

        double mainKg = safeParse(mainWeightField.getText());

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

            price1Field.setDisable(false);
            price2Field.setDisable(false);
            priceStack.setVisible(true);
            priceStack.setManaged(true);
            price1Field.requestFocus();
            return;
        }

        double dustValue = entries.get(lastNonNullIndex);

        List<Double> preDust = new ArrayList<>();
        for (int i = 0; i < lastNonNullIndex; i++) preDust.add(entries.get(i));

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

        List<Double> finalSWs = new ArrayList<>();
        for (Double d : preDust) finalSWs.add(d == null ? 0.0 : d);
        finalSWs.add(dustValue);

        swArea.getChildren().clear();
        swFields.clear(); priceFields.clear(); qualityFields.clear();

        for (int i = 0; i < finalSWs.size(); i++) {
            double val = finalSWs.get(i);
            boolean isDust = (i == finalSWs.size() - 1);

            TextField swR = makeReadOnly(360, moneyFmt.format(val));
            Label mul = makeMultiplyLabel();
            TextField priceRes = makePriceResult(220);
            priceRes.setVisible(true);
            priceRes.setManaged(true);
            TextField qual = makeQualityField(180);

            if (isDust) {
                Region mulEmpty = new Region(); mulEmpty.setPrefWidth(30);
                Region priceEmpty = new Region(); priceEmpty.setPrefWidth(220);

                ComboBox<String> discount = new ComboBox<>();
                discount.setEditable(true);
                discount.getItems().addAll("1.5", "N", "1");
                discount.setValue("1.5");
                discount.setPrefWidth(180);
                discount.setStyle("-fx-border-color: black; -fx-border-width: 3;");
                discount.getEditor().setFont(Font.font(fontSize.get() * 0.85));

                discount.focusedProperty().addListener((obs, oldV, newV) -> {
                    if (newV) Platform.runLater(discount::show);
                });

                discount.setOnKeyPressed(ev -> {
                    switch (ev.getCode()) {
                        case DOWN:
                        case UP:
                            discount.show();
                            break;
                        case ENTER:
                            String entered = discount.getEditor().getText();
                            if (!discount.getItems().contains(entered)) discount.setValue(entered);
                            showTotals();
                            ev.consume();
                            break;
                    }
                });

                discount.getEditor().setOnKeyPressed(ev -> {
                    if (ev.getCode() == KeyCode.ENTER) {
                        String entered = discount.getEditor().getText();
                        if (!discount.getItems().contains(entered)) discount.setValue(entered);
                        showTotals();
                        ev.consume();
                    }
                });

                discount.valueProperty().addListener((o, ov, nv) -> updateTotalsIfVisible());
                discount.getEditor().textProperty().addListener((o, ov, nv) -> updateTotalsIfVisible());

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(swR, mulEmpty, priceEmpty, discount);
                swArea.getChildren().add(row);

                swFields.add(swR);
                priceFields.add(null);
                qualityFields.add(discount.getEditor());
                dustDiscountBox = discount;
                continue;
            } else {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(swR, mul, priceRes, qual);
                swArea.getChildren().add(row);

                swFields.add(swR);
                priceFields.add(priceRes);
                qualityFields.add(qual);
            }
        }

        price1Field.setDisable(false);
        price2Field.setDisable(false);
        price1Field.requestFocus();

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
        if (priceOut == null) return;

        double p1 = safeParse(price1Field.getText());
        double p2 = safeParse(price2Field.getText());
        double qv = safeParse(q.getText());

        double rowDisplayRate = p1 + p2 + qv;
        priceOut.setText(moneyFmt.format(rowDisplayRate));

        double actualPrice = rowDisplayRate * (swKg / 1000.0);
        priceOut.setUserData(actualPrice);

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

    // ---------------- Totals, GST UI ----------------
    private void showTotals() {
        updateTotalsIfVisible();

        totalsArea.getChildren().clear();

        Region topLine = new Region();
        topLine.setPrefHeight(6);
        topLine.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        topLine.setMaxWidth(Double.MAX_VALUE);

        Label totalVal = new Label(totalsFormatted());
        totalVal.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        totalVal.setAlignment(Pos.CENTER);

        gstField = new TextField();
        gstField.setPromptText("GST Amount (flat)");
        gstField.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get() * 0.9), fontSize));
        gstField.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-background-color: white;");
        gstField.setAlignment(Pos.CENTER);
        gstField.setMaxWidth(260);

        // CENTER box contains the TOTAL (value) and the GST input (value only)
        VBox centerBox = new VBox(10);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.getChildren().addAll(totalVal, gstField);

        Region bottomLine = new Region();
        bottomLine.setPrefHeight(6);
        bottomLine.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        bottomLine.setMaxWidth(Double.MAX_VALUE);

        Label finalVal = new Label(totalsFormatted());
        finalVal.setId("finalValue");
        finalVal.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(fontSize.get()), fontSize));
        finalVal.setAlignment(Pos.CENTER);

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
        for (int i = 0; i < priceFields.size(); i++) {
            TextField pf = priceFields.get(i);
            if (pf == null || !pf.isVisible()) continue;
            Object ud = pf.getUserData();
            if (ud instanceof Double) total += (Double) ud;
        }

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

    // ---------------- Print implementation ----------------
    // User choices: left half A4, 15mm margins, wide spacing, auto-shrink font, only values, GST value printed without label
    // Helper: draw left + right on same baseline, then advance Y
    // =====================================================================
    //  DRAW LEFT + RIGHT ON SAME BASELINE (for SW rows)
    // =====================================================================
    private void drawRow(PDPageContentStream cs, PDType1Font font,
                        String left, String right,
                        float xLeft, float xRight,
                        final float[] y, float fontSize) throws IOException {

        // LEFT
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(xLeft, y[0] - fontSize);
        cs.showText(left);
        cs.endText();

        // RIGHT (if present)
        if (right != null && !right.isEmpty()) {
            float w = font.getStringWidth(right) / 1000f * fontSize;
            float startX = xRight - w;

            cs.setFont(font, fontSize);
            cs.beginText();
            cs.newLineAtOffset(startX, y[0] - fontSize);
            cs.showText(right);
            cs.endText();
        }

        // Move down ONCE after both are printed
        y[0] -= (fontSize + 1);   // tighter spacing
    }

    // =====================================================================
    //  CENTERED TEXT (for TOTAL / GST / FINAL)
    // =====================================================================
    private void drawCenter(PDPageContentStream cs, PDType1Font font,
                            String text, float xCenter,
                            final float[] y, float fs) throws IOException {

        float w = font.getStringWidth(text) / 1000f * fs;
        float startX = xCenter - (w / 2f);

        cs.setFont(font, fs);
        cs.beginText();
        cs.newLineAtOffset(startX, y[0] - fs);
        cs.showText(text);
        cs.endText();

        y[0] -= (fs + 2);   // tight spacing
    }

    // =====================================================================
    //  FINAL MERGED printSlip() — EXACT to your notebook layout
    // =====================================================================
    private void printSlip() {
        try (PDDocument doc = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDRectangle rect = page.getMediaBox();
            float pageW = rect.getWidth();
            float pageH = rect.getHeight();

            // ZERO MARGINS - but add top margin for pinning
            float marginLeft = 0f;
            float marginTop  = 0f;

            float xLeft   = 2f;      // just 2px from left edge
            float xRight  = 262f;    // adjust right boundary
            float xCenter = 132f;    // center point

            // START WITH TOP MARGIN for pinning area
            final float[] y = { pageH - 40f };  // 40px from top instead of 2px

            PDType1Font font = PDType1Font.HELVETICA;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ----------------------------------------------------
                // HEADER PRICE 1 + TRUCK
                // ----------------------------------------------------
                String p1 = price1Field.getText().trim();
                String truck = truckNumberField.getText().trim();

                if (!p1.isEmpty()) {

                    float baseline = y[0] - 10f;

                    // Price1 LEFT
                    cs.setFont(font, 14f);
                    cs.beginText();
                    cs.newLineAtOffset(xLeft, baseline);
                    cs.showText(p1);
                    cs.endText();

                    // Truck RIGHT
                    if (!truck.isEmpty()) {
                        float tw = font.getStringWidth(truck) / 1000f * 12f;
                        float tx = xRight - tw;

                        cs.setFont(font, 12f);
                        cs.beginText();
                        cs.newLineAtOffset(tx, baseline);
                        cs.showText(truck);
                        cs.endText();
                    }

                    y[0] -= 20f;
                }

                // Price2
                String p2 = price2Field.getText().trim();
                if (!p2.isEmpty()) {
                    cs.setFont(font, 14f);
                    cs.beginText();
                    cs.newLineAtOffset(xLeft, y[0] - 12f);
                    cs.showText(p2);
                    cs.endText();
                    y[0] -= 22f;
                }

                // ADD LINE AFTER PRICE2
                cs.setLineWidth(1f);
                cs.moveTo(xLeft, y[0]);
                cs.lineTo(xLeft + 60f, y[0]);
                cs.stroke();
                y[0] -= 15f;  // Extra gap after line

                // SUBWEIGHT ROWS
                String main = mainWeightField.getText().trim();
                int n = swFields.size();

                // Calculate offset for alignment
                float swAlignOffset = 0f;
                if (n > 0) {
                    String firstRowPrefix = main + " – ";
                    swAlignOffset = font.getStringWidth(firstRowPrefix) / 1000f * 14f;
                }

                for (int i = 0; i < n; i++) {
                    String sw = String.valueOf((int)Math.floor(safeParse(swFields.get(i).getText())));
                    String rate = (i < priceFields.size() && priceFields.get(i) != null)
                            ? String.valueOf((int)Math.floor(safeParse(priceFields.get(i).getText()))) : "";
                    String qv = (i < qualityFields.size() && qualityFields.get(i) != null)
                            ? qualityFields.get(i).getText().trim() : "";

                    String leftText;
                    float rowXLeft = xLeft;

                    if (i == 0) {
                        leftText = main + " – " + sw + " × " + rate;
                    } else if (i == n - 1) {
                        leftText = sw;
                        rowXLeft = xLeft + swAlignOffset;  // Align with first SW
                    } else {
                        leftText = sw + " × " + rate;
                        rowXLeft = xLeft + swAlignOffset;  // Align with first SW
                    }

                    drawRow(cs, font, leftText, qv, rowXLeft, xRight, y, 14f);
                }

                // ----------------------------------------------------
                // FIRST LINE
                // ----------------------------------------------------
                cs.setLineWidth(2f);
                cs.moveTo(xLeft, y[0]);
                cs.lineTo(xLeft + 300f, y[0]);
                cs.stroke();
                y[0] -= 14f;

                // ----------------------------------------------------
                // TOTAL CENTER
                // ----------------------------------------------------
                String totalFloor = String.valueOf((int)Math.floor(totalsValue()));
                drawCenter(cs, font, totalFloor, xCenter, y, 18f);

                // ----------------------------------------------------
                // GST CENTER
                // ----------------------------------------------------
                String gst = gstField.getText().trim();
                if (gst.isEmpty()) gst = "0";
                String gstFloor = String.valueOf((int)Math.floor(safeParse(gst)));
                drawCenter(cs, font, gstFloor, xCenter, y, 16f);

                y[0] -= 6f;

                // ----------------------------------------------------
                // SECOND LINE
                // ----------------------------------------------------
                cs.setLineWidth(2f);
                cs.moveTo(xLeft, y[0]);
                cs.lineTo(xLeft + 300f, y[0]);
                cs.stroke();
                y[0] -= 14f;

                // ----------------------------------------------------
                // FINAL CENTER VALUE
                // ----------------------------------------------------
                double finalV = totalsValue() + safeParse(gst);
                String finalFloor = String.valueOf((int)Math.floor(finalV));
                drawCenter(cs, font, finalFloor, xCenter, y, 18f);
            }

            // AUTO PRINT with printer selection
            PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);

            if (printers.length > 0) {
                PrinterJob job = PrinterJob.getPrinterJob();
                
                // Show print dialog to let user choose printer
                if (job.printDialog()) {
                    job.setPrintable(new PDFPrintable(doc));
                    job.print();
                    return;
                }
            }

            // SAVE DIALOG
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Slip PDF");
            chooser.setInitialFileName("Rajdhani_Slip.pdf");
            chooser.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

            File saveFile = chooser.showSaveDialog(overlay.getScene().getWindow());
            if (saveFile != null) doc.save(saveFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



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

        if (truckNumberField != null) truckNumberField.clear();

        if (bottomButtons != null) {
            bottomButtons.setVisible(false);
            bottomButtons.setManaged(false);
        }

        root.setLeft(null);
        buildLeftContainer();
        Platform.runLater(() -> truckNumberField.requestFocus());

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

        rightScrollPane = new ScrollPane(rightContainer);
        rightScrollPane.setFitToWidth(true);
        rightScrollPane.setFitToHeight(true);
        rightScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScrollPane.setStyle("-fx-background: white; -fx-background-color: white;");
        rightContainer.setStyle("-fx-background-color: white;");
        root.setCenter(rightScrollPane);

        mainWeightField.clear();

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
