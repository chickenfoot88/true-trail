/*
 * Professional JForex programming service
 * https://fxdiler.com/
 * email: support@fxdiler.com
 * skype: fxdiler
 */
/*
 True Trail v2.1
*/

 package jforex;

 import com.dukascopy.api.Configurable;
 import com.dukascopy.api.Filter;
 import com.dukascopy.api.IAccount;
 import com.dukascopy.api.IBar;
 import com.dukascopy.api.IChart;
 import com.dukascopy.api.IConsole;
 import com.dukascopy.api.IContext;
 import com.dukascopy.api.ICurrency;
 import com.dukascopy.api.IDataService;
 import com.dukascopy.api.IEngine;
 import com.dukascopy.api.IHistory;
 import com.dukascopy.api.IMessage;
 import com.dukascopy.api.IOrder;
 import com.dukascopy.api.IStrategy;
 import com.dukascopy.api.ITick;
 import com.dukascopy.api.ITimeDomain;
 import com.dukascopy.api.Instrument;
 import com.dukascopy.api.JFException;
 import com.dukascopy.api.OfferSide;
 import com.dukascopy.api.Period;
 import com.dukascopy.api.RequiresFullAccess;
 import com.dukascopy.api.drawings.IChartObjectFactory;
 import com.dukascopy.api.drawings.ICustomWidgetChartObject;
 import com.dukascopy.api.feed.IBarFeedListener;
 import java.awt.Color;
 import java.awt.Component;
 import java.awt.Dimension;
 import java.awt.Font;
 import java.io.File;
 import java.io.IOException;
 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.concurrent.TimeUnit;
 import javax.sound.sampled.AudioFormat;
 import javax.sound.sampled.AudioInputStream;
 import javax.sound.sampled.AudioSystem;
 import javax.sound.sampled.Clip;
 import javax.sound.sampled.DataLine;
 import javax.sound.sampled.LineUnavailableException;
 import javax.sound.sampled.UnsupportedAudioFileException;
 import javax.swing.JDialog;
 import javax.swing.JLabel;
 import javax.swing.JOptionPane;
 import javax.swing.JPanel;
 import javax.swing.SwingConstants;
 
 @RequiresFullAccess
 public class TrueTrail implements IStrategy {

    static class NoLossMode {
        public static final NoLossMode NO_LOSS_NOT_SETTLED = new NoLossMode("Not settled", 0);
        public static final NoLossMode NO_LOSS_MANUAL = new NoLossMode("Manual no loss", 1);
        public static final NoLossMode NO_LOSS_BY_ATR = new NoLossMode("No loss by ATR", 2);
        
        public final String name;
        public final Number id;        
        
        public NoLossMode(String name, Number id){
            this.name = name;
            this.id = id;
        }
        
        @Override 
        public String toString(){
            return name;
        }
    }
     
     @Configurable(value = "Instrument")                 public Instrument instrument = Instrument.EURUSD;
     @Configurable(value = "Set StopLoss")               public boolean isSetSL = true;
     @Configurable(value = "StopLoss, pips")             public double stopLoss = 50.0;
     @Configurable(value = "Set TakeProfit")             public boolean isSetTP = true;
     @Configurable(value = "TakeProfit, pips")           public double takeProfit = 100.0;
     @Configurable(value = "Set NoLoss Mode")            public NoLossMode noLossMode = NoLossMode.NO_LOSS_NOT_SETTLED;
     @Configurable(value = "NoLoss Manual, pips")        public double noLossManual = 12.0;
     @Configurable(value = "NoLoss by ATR, %")           public double noLossByATR = 20;
     @Configurable(value = "Delta NoLoss, pips")         public double deltaNoLoss = 2.0;
     @Configurable(value = "Trail SL")                   public boolean isTrailSL = true;
     @Configurable(value = "SL trail, pips")             public double trailStopLoss = 20.0;
     @Configurable(value = "SL trail step, pips")        public double trailStepSL = 2.0;
     @Configurable(value = "Trail TP")                   public boolean isTrailTP = false;
     @Configurable(value = "TP trail, pips")             public double trailTakeProfit = 10.0;
     @Configurable(value = "TP trail step, pips")        public double trailStepTP = 10.0;
     @Configurable(value = "ATR Period")                 public Period periodATR = Period.ONE_HOUR;
     @Configurable(value = "ATR Bars Count")             public int barsCountATR = 200;
     @Configurable(value = "Enable Sound")               public boolean enableSound = false;
     @Configurable(value = "Sound NoLoss")               public File soundNoLoss = new File("noloss.wav");
     @Configurable(value = "Sound Trail")                public File soundTrail = new File("trail.wav");
     @Configurable(value = "Sound Close Position")       public File soundClose = new File("close.wav");
     @Configurable(value = "Sound Open Position")        public File soundOpen = new File("open.wav");
     @Configurable(value = "Widget Size, %")             public int widgetSize = 100;
     @Configurable(value = "Profit color")               public Color profitColor = Color.GREEN;
     @Configurable(value = "Loss color")                 public Color lossColor = Color.RED;
     
     
     private IEngine engine;
     private IConsole console;
     private IHistory history;
     private IContext context;
     private IChart chart;
     private IChartObjectFactory factory;
     private IDataService dataService;
     private ICurrency currency;
     private String widgetName;
     private Map<String, Long> timeMap;
     private final long delay = 1500;
     private double ATR;
     private List<IOrder> historyOrders;
     public double noLoss;
     public boolean isNoLossTriggered;
     
     @Override
     public void onStart(IContext context) throws JFException {
         this.engine = context.getEngine();
         this.console = context.getConsole();
         this.history = context.getHistory();
         this.dataService = context.getDataService();
         this.context = context;
         this.currency = context.getAccount().getAccountCurrency();
         
         boolean isStarted = true;
         widgetName = this.getClass().getSimpleName();
         this.chart = context.getChart(instrument);
         if (chart != null) {
             this.factory = chart.getChartObjectFactory();
         } else {
             print("Chart not opened for %s", instrument.name());
             showWarning(String.format("Chart not opened for %s", instrument.name()));
             isStarted = false;
             context.stop();
         }
         
         if (enableSound && engine.getType() != IEngine.Type.TEST) {
             if (!soundNoLoss.exists()) {
                 isStarted = false;
                 print("File "+soundNoLoss.getName()+" does not exist");
                 showWarning("File "+soundNoLoss.getName()+" does not exist");
                 context.stop();
             }
             if (!soundTrail.exists()) {
                 isStarted = false;
                 print("File "+soundTrail.getName()+" does not exist");
                 showWarning("File "+soundTrail.getName()+" does not exist");
                 context.stop();
             }
             if (!soundClose.exists()) {
                 isStarted = false;
                 print("File "+soundClose.getName()+" does not exist");
                 showWarning("File "+soundClose.getName()+" does not exist");
                 context.stop();
             }
             if (!soundOpen.exists()) {
                 isStarted = false;
                 print("File "+soundOpen.getName()+" does not exist");
                 showWarning("File "+soundOpen.getName()+" does not exist");
                 context.stop();
             }
         }
         
         if (isStarted) {
             
             ATR = getATR();
             timeMap = new HashMap();
             historyOrders = new ArrayList<>();
             updateHistoryOrders();
             
             context.subscribeToBarsFeed(instrument, periodATR, OfferSide.BID, new IBarFeedListener() {
                 @Override
                 public void onBar(Instrument instrument, Period period, OfferSide offerSide, IBar bar) {
                     try {
                         long currentTime = bar.getTime() + period.getInterval();
                         if (!isOffline(currentTime)) {
                             ATR = getATR();
                             updateWidgetComponents();
                         }
                     } catch (JFException e) {
                         console.getErr().println(e);
                     }
                 }
             });

             if(noLossMode != NoLossMode.NO_LOSS_NOT_SETTLED) {
                if(noLossMode == NoLossMode.NO_LOSS_MANUAL) {
                    noLoss = noLossManual;
                } else if (noLossMode == NoLossMode.NO_LOSS_BY_ATR) {
                    noLoss = ATR * noLossByATR/100;
                };
            }
             
             addWidget(chart);
             updateWidgetComponents();
         }
     }
 
     @Override
     public void onTick(Instrument instrument, ITick tick) throws JFException {
         if (instrument == this.instrument) {
             for (IOrder order : engine.getOrders(instrument)) {
                 if (order.getState() == IOrder.State.FILLED) {
                     if (noLossMode != NoLossMode.NO_LOSS_NOT_SETTLED) {
                         if (order.getProfitLossInPips() >= noLoss) {
                             if (order.isLong()) {  // BUY
                                 double sl = nd(order.getOpenPrice() + deltaNoLoss*getPoint(), getDigits());
                                 if (order.getStopLossPrice() < sl) {
                                     isNoLossTriggered = true;
                                     order.setStopLossPrice(sl);
                                     if (enableSound && engine.getType() != IEngine.Type.TEST) {
                                         playSound(soundNoLoss);
                                     }
                                  } else if(order.getStopLossPrice() == sl) {
                                     isNoLossTriggered = true;
                                 }
                             }
                             if (!order.isLong()) {  // SELL
                                 double sl = nd(order.getOpenPrice() - deltaNoLoss*getPoint(), getDigits());
                                 if (order.getStopLossPrice() > sl || order.getStopLossPrice() == 0) {
                                     order.setStopLossPrice(sl);
                                     isNoLossTriggered = true;
                                     if (enableSound && engine.getType() != IEngine.Type.TEST) {
                                         playSound(soundNoLoss);
                                     }
                                 }
                             }
                         }
                     }
                     if (isTrailSL) {
                         if (order.isLong()) {  // BUY
                             double sl = nd(tick.getBid() - trailStopLoss*getPoint(), getDigits());
                             if (tick.getBid() > order.getOpenPrice() + trailStopLoss*getPoint()) {
                                 if (sl - order.getStopLossPrice() >= trailStepSL*getPoint()) {
                                     long time = timeMap.get(order.getLabel()) != null ? timeMap.get(order.getLabel()) : 0;
                                     if (tick.getTime() - time > delay) {
                                         timeMap.put(order.getLabel(), tick.getTime());
                                         order.setStopLossPrice(sl);
                                     }
                                 }
                             }
                         }
                         if (!order.isLong()) {  // SELL
                             double sl = nd(tick.getAsk() + trailStopLoss*getPoint(), getDigits());
                             if (tick.getAsk() < order.getOpenPrice() - trailStopLoss*getPoint()) {
                                 if (order.getStopLossPrice() - sl >= trailStepSL*getPoint()) {
                                     long time = timeMap.get(order.getLabel()) != null ? timeMap.get(order.getLabel()) : 0;
                                     if (tick.getTime() - time > delay) {
                                         timeMap.put(order.getLabel(), tick.getTime());
                                         order.setStopLossPrice(sl);
                                     }
                                 }
                             }
                         }
                     }
                     if (isTrailTP) {
                         if (order.isLong()) {  // BUY
                             if (order.getTakeProfitPrice() - tick.getBid() <= trailTakeProfit*getPoint()) {
                                 double tp = nd(order.getTakeProfitPrice() + trailStepTP*getPoint(), getDigits());
                                 order.setTakeProfitPrice(tp);
                             }
                         }
                         if (!order.isLong()) {  // SELL
                             if (tick.getAsk() - order.getTakeProfitPrice() <= trailTakeProfit*getPoint()) {
                                 double tp = nd(order.getTakeProfitPrice() - trailStepTP*getPoint(), getDigits());
                                 order.setTakeProfitPrice(tp);
                             }
                         }
                     }
                 }
             }
             updateWidgetComponents();
         }
     }
 
     @Override
     public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
     }
 
     @Override
     public void onMessage(IMessage message) throws JFException {
         if (message.getType() == IMessage.Type.ORDER_FILL_OK) {
             IOrder order = message.getOrder();
             if (order.getInstrument() == this.instrument) {
                 double price = order.getOpenPrice();
                 if (isSetSL && order.getStopLossPrice() == 0) {
                     double sl = order.isLong() ? nd(price - stopLoss*getPoint(), getDigits()) : nd(price + stopLoss*getPoint(), getDigits());
                     order.setStopLossPrice(sl);
                 }
                 if (isSetTP && order.getTakeProfitPrice() == 0) {
                     double tp = order.isLong() ? nd(price + takeProfit*getPoint(), getDigits()) : nd(price - takeProfit*getPoint(), getDigits());
                     order.setTakeProfitPrice(tp);
                 }
                 if (enableSound && engine.getType() != IEngine.Type.TEST) {
                     playSound(soundOpen);
                 }
                 updateWidgetComponents();
             }
         }
         if (message.getType() == IMessage.Type.ORDER_CLOSE_OK) {
             IOrder order = message.getOrder();
             if (order.getInstrument() == this.instrument) {
                 if (order.getState() == IOrder.State.CLOSED) {
                     historyOrders.add(order);
                 }
                 if (enableSound && engine.getType() != IEngine.Type.TEST) {
                     playSound(soundClose);
                 }
                 updateWidgetComponents();
             }
         }
     }
 
     @Override
     public void onAccount(IAccount account) throws JFException {
     }
 
     @Override
     public void onStop() throws JFException {
         if (chart != null) {
             ICustomWidgetChartObject widget = (ICustomWidgetChartObject) chart.get(widgetName);
             if (widget != null) {
                 chart.remove(widget);
             }
         }
     }
     
     private void print(Object o) {
         console.getOut().println(o);
     }
     
     private void print(String format, Object... args) {
         console.getOut().format(format, args).println();
     }
     
     private void showWarning(String text) {
         JOptionPane optionPane = new JOptionPane(text, JOptionPane.WARNING_MESSAGE);
         JDialog dialog = optionPane.createDialog(this.getClass().getSimpleName());
         dialog.setVisible(true);
     }
     
     private void addWidget(final IChart chart) throws JFException {
         int widgetWidth = 320*widgetSize/100;
         int widgetHeight = 115*widgetSize/100;
         int captionWidth = widgetWidth;
         int captionHeight = widgetHeight/10;
         int labelWidth = (int) (widgetWidth/2.2);
         int labelHeight = widgetHeight/10;
         int componentWidth = (int) (widgetWidth/2.2);
         int componentHeight = widgetHeight/10;
         int captionFontSize = widgetHeight/10;
         int labelFontSize = widgetHeight/10 - 1;
         final Dimension widgetDimension = new Dimension(widgetWidth, widgetHeight);
                 
         // Безубыток
         JLabel jLabelNoLoss = new JLabel(noLossMode == NoLossMode.NO_LOSS_BY_ATR ? "Безубыток по ATR: " : "Безубыток: ");
         jLabelNoLoss.setForeground(chart.getCommentColor());
         jLabelNoLoss.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelNoLoss.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelNoLoss.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelNoLoss.setPreferredSize(new Dimension(labelWidth, labelHeight));
         
         JLabel jLabelNoLossValue = new JLabel(String.format("Порог: %.1f pips", noLoss) + " / " + String.format("Размер: %.1f", deltaNoLoss));
         jLabelNoLossValue.setName("WIDGET_NOLOSS");
         jLabelNoLossValue.setForeground(chart.getCommentColor());
         jLabelNoLossValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelNoLossValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         // BUY & SELL
         JLabel jLabelBuyValue = new JLabel(String.format("BUY %d (%.3f)", 0, 0.000));
         jLabelBuyValue.setName("WIDGET_BUYVAL");
         jLabelBuyValue.setForeground(chart.getCommentColor());
         jLabelBuyValue.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelBuyValue.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelBuyValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelBuyValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         JLabel jLabelSellValue = new JLabel(String.format("SELL %d (%.3f)", 0, 0.000));
         jLabelSellValue.setName("WIDGET_SELLVAL");
         jLabelSellValue.setForeground(chart.getCommentColor());
         jLabelSellValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelSellValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
 
         // Открыто
         JLabel jLabelOpenProfit = new JLabel("Открыто: ");
         jLabelOpenProfit.setForeground(chart.getCommentColor());
         jLabelOpenProfit.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelOpenProfit.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelOpenProfit.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelOpenProfit.setPreferredSize(new Dimension(labelWidth, labelHeight));
         
         JLabel jLabelOpenProfitValue = new JLabel(String.format("%.2f %s", 0.0, currency.toString()));
         jLabelOpenProfitValue.setName("WIDGET_OPENPROFIT");
         jLabelOpenProfitValue.setForeground(profitColor);
         jLabelOpenProfitValue.setForeground(chart.getCommentColor());
         jLabelOpenProfitValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelOpenProfitValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         // Сегодня
         JLabel jLabelTodayProfit = new JLabel("Сегодня: ");
         jLabelTodayProfit.setForeground(chart.getCommentColor());
         jLabelTodayProfit.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelTodayProfit.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelTodayProfit.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelTodayProfit.setPreferredSize(new Dimension(labelWidth, labelHeight));
         
         JLabel jLabelTodayProfitValue = new JLabel(String.format("%.2f %s", 0.0, currency.toString()));
         jLabelTodayProfitValue.setName("WIDGET_TODAYPROFIT");
         jLabelTodayProfitValue.setForeground(profitColor);
         jLabelTodayProfitValue.setForeground(chart.getCommentColor());
         jLabelTodayProfitValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelTodayProfitValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         // Всего
         JLabel jLabelTotalProfit = new JLabel("Всего: ");
         jLabelTotalProfit.setForeground(chart.getCommentColor());
         jLabelTotalProfit.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelTotalProfit.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelTotalProfit.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelTotalProfit.setPreferredSize(new Dimension(labelWidth, labelHeight));
         
         JLabel jLabelTotalProfitValue = new JLabel(String.format("%.2f %s", 0.0, currency.toString()));
         jLabelTotalProfitValue.setName("WIDGET_TOTALPROFIT");
         jLabelTotalProfitValue.setForeground(profitColor);
         jLabelTotalProfitValue.setForeground(chart.getCommentColor());
         jLabelTotalProfitValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelTotalProfitValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         // ATR
         JLabel jLabelATR = new JLabel("ATR: ");
         jLabelATR.setForeground(chart.getCommentColor());
         jLabelATR.setHorizontalAlignment(SwingConstants.LEFT);
         jLabelATR.setHorizontalTextPosition(SwingConstants.LEFT);
         jLabelATR.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelATR.setPreferredSize(new Dimension(labelWidth, labelHeight));
         
         JLabel jLabelATRValue = new JLabel(String.format("%.1f pips", ATR));
         jLabelATRValue.setName("WIDGET_ATR");
         jLabelATRValue.setForeground(chart.getCommentColor());
         jLabelATRValue.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));
         jLabelATRValue.setPreferredSize(new Dimension(componentWidth, componentHeight));
         
         // create widget
         float posX = 0.01f;
         float posY = 0.01f;
         ICustomWidgetChartObject widget = chart.getChartObjectFactory().createChartWidget(widgetName);
         widget.setPreferredSize(widgetDimension);
         widget.setFillOpacity(0.0f);
         widget.setPosX(posX);
         widget.setPosY(posY);
         widget.setVisibleInWorkspaceTree(false);
         JPanel panel = widget.getContentPanel();
         panel.setMaximumSize(widgetDimension);
         panel.setMinimumSize(widgetDimension);
         panel.add(jLabelNoLoss);
         panel.add(jLabelNoLossValue);
         panel.add(jLabelBuyValue);
         panel.add(jLabelSellValue);
         panel.add(jLabelOpenProfit);
         panel.add(jLabelOpenProfitValue);
         panel.add(jLabelTodayProfit);
         panel.add(jLabelTodayProfitValue);
         panel.add(jLabelTotalProfit);
         panel.add(jLabelTotalProfitValue);
         panel.add(jLabelATR);
         panel.add(jLabelATRValue);
         chart.add(widget);
         chart.repaint();
     }
     
     private void playSound(File wavFile) {
         try {
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile);
             AudioFormat af = audioInputStream.getFormat();
             int nSize = (int) (af.getFrameSize() * audioInputStream.getFrameLength());
             byte[] audio = new byte[nSize];
             DataLine.Info info = new DataLine.Info(Clip.class, af, nSize);
             audioInputStream.read(audio, 0, nSize);
             Clip clip = (Clip) AudioSystem.getLine(info);
             clip.open(af, audio, 0, nSize);
             clip.start();
         } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
             console.getErr().println(ex);
         }
     }
     
     private int getDigits() {
         return instrument.getPipScale()+1;
     }
     
     private double getPoint() throws JFException {
         return instrument.getPipValue();
     }
     
     private double nd(double value, int digits) {
         long val = Math.round(value * Math.pow(10, digits));
         return ((double) val) / ((double)Math.pow(10, digits));
     }
     
     private boolean isOffline(long time) throws JFException {
         Set<ITimeDomain> offlines = dataService.getOfflineTimeDomains(time - Period.WEEKLY.getInterval(), time + Period.WEEKLY.getInterval());
         for (ITimeDomain offline : offlines) {
             if (time > offline.getStart() && time < offline.getEnd()) {
                 return true;
             }
         }
         return false;
     }
     
     private void updateWidgetComponents() throws JFException {
         ICustomWidgetChartObject widget = (ICustomWidgetChartObject) chart.get(widgetName);
         if (widget != null) {
             int longCount = getLongCount();
             int shortCount = getShortCount();
             double longAmount = getLongAmount();
             double shortAmount = getShortAmount();
             double openProfit = getOpenProfit();
             double todayProfit = getTodayProfit();
             double totalProfit = getTotalProfit();
             JPanel panel = widget.getContentPanel();
             Component[] components = panel.getComponents();
             for (Component component : components) {
                if(isNoLossTriggered && component.getName() != null && component.getName().equals("WIDGET_NOLOSS")) {
                    JLabel jLabelNoLossValue = (JLabel) component;
                    jLabelNoLossValue.setForeground(profitColor);
                }
                
                 if (component.getName() != null && component.getName().equals("WIDGET_BUYVAL")) {
                     JLabel jLabelBuyValue = (JLabel) component;
                     jLabelBuyValue.setText(String.format("BUY %d (%.3f)", longCount, longAmount));
                 }
                 if (component.getName() != null && component.getName().equals("WIDGET_SELLVAL")) {
                     JLabel jLabelSellValue = (JLabel) component;
                     jLabelSellValue.setText(String.format("SELL %d (%.3f)", shortCount, shortAmount));
                 }
                 if (component.getName() != null && component.getName().equals("WIDGET_OPENPROFIT")) {
                     JLabel jLabelOpenProfitValue = (JLabel) component;
                     jLabelOpenProfitValue.setText(String.format("%.2f %s", openProfit, currency.toString()));
                     if (openProfit < 0) {
                         jLabelOpenProfitValue.setForeground(lossColor);
                     } else {
                         jLabelOpenProfitValue.setForeground(profitColor);
                     }
                 }
                 if (component.getName() != null && component.getName().equals("WIDGET_TODAYPROFIT")) {
                     JLabel jLabelTodayProfitValue = (JLabel) component;
                     jLabelTodayProfitValue.setText(String.format("%.2f %s", todayProfit, currency.toString()));
                     if (todayProfit < 0) {
                         jLabelTodayProfitValue.setForeground(lossColor);
                     } else {
                         jLabelTodayProfitValue.setForeground(profitColor);
                     }
                 }
                 if (component.getName() != null && component.getName().equals("WIDGET_TOTALPROFIT")) {
                     JLabel jLabelTotalProfitValue = (JLabel) component;
                     jLabelTotalProfitValue.setText(String.format("%.2f %s", totalProfit, currency.toString()));
                     if (totalProfit < 0) {
                         jLabelTotalProfitValue.setForeground(lossColor);
                     } else {
                         jLabelTotalProfitValue.setForeground(profitColor);
                     }
                 }
                 if (component.getName() != null && component.getName().equals("WIDGET_ATR")) {
                     JLabel jLabelATRValue = (JLabel) component;
                     jLabelATRValue.setText(String.format("%.1f pips", ATR) + " / " + periodATR + " / " + barsCountATR);
                 }
             }
         }
     }
     
     private int getLongCount() throws JFException {
         int count = 0;
         for (IOrder order : engine.getOrders(instrument)) {
             if (order.isLong() && order.getState() == IOrder.State.FILLED) {
                 count++;
             }
         }
         return count;
     }
     
     private int getShortCount() throws JFException {
         int count = 0;
         for (IOrder order : engine.getOrders(instrument)) {
             if (!order.isLong() && order.getState() == IOrder.State.FILLED) {
                 count++;
             }
         }
         return count;
     }
     
     private double getLongAmount() throws JFException {
         double amount = 0;
         for (IOrder order : engine.getOrders(instrument)) {
             if (order.isLong() && order.getState() == IOrder.State.FILLED) {
                 amount += order.getAmount();
             }
         }
         return amount;
     }
     
     private double getShortAmount() throws JFException {
         double amount = 0;
         for (IOrder order : engine.getOrders(instrument)) {
             if (!order.isLong() && order.getState() == IOrder.State.FILLED) {
                 amount += order.getAmount();
             }
         }
         return amount;
     }
     
     private double getATR() throws JFException {
         IBar lastBar = history.getBar(instrument, periodATR, OfferSide.BID, 1);
         List<IBar> bars = history.getBars(instrument, periodATR, OfferSide.BID, Filter.WEEKENDS, barsCountATR, lastBar.getTime(), 0);
         double sum = 0.0;
         for (IBar bar : bars) {
             sum += (bar.getHigh() - bar.getLow())/getPoint();
         }
         double result = sum / barsCountATR;
         return result;
     }
     
     private double getOpenProfit() throws JFException {
         double profit = 0;
         for (IOrder order : engine.getOrders(instrument)) {
             if (order.getState() == IOrder.State.FILLED) {
                 profit += order.getProfitLossInAccountCurrency();
             }
         }
         return profit;
     }
     
     private void updateHistoryOrders() throws JFException {
         long time = history.getLastTick(instrument).getTime();
         long from = time - TimeUnit.DAYS.toMillis(365);
         long to = time;
         List<IOrder> orders = history.getOrdersHistory(instrument, from, to);
         if (!orders.isEmpty()) {
             historyOrders.addAll(orders);
         }
     }
     
     private double getTodayProfit() throws JFException {
         double profit = 0;
         IBar bar = history.getBar(instrument, Period.DAILY, OfferSide.BID, 0);
         long time = bar.getTime();
         for (IOrder order : historyOrders) {
             if (order.getCloseTime() > time) {
                 profit += (order.getProfitLossInAccountCurrency() - order.getCommission());
             }
         }
         return profit;
     }
     
     private double getTotalProfit() throws JFException {
         double profit = 0;
         for (IOrder order : historyOrders) {
             profit += (order.getProfitLossInAccountCurrency() - order.getCommission());
         }
         return profit;
     }
 }

 