package utils.log;


import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.TimeZone;

import javax.swing.JPanel;

import utils.NMEAAnalyzer.ScalarValue;

public class LogAnalysisPanel
     extends JPanel
  implements MouseListener, MouseMotionListener
{
  @SuppressWarnings("compatibility:2563996868822874868")
  public final static long serialVersionUID = 1L;
  
  private transient Map<Date, ScalarValue> logdata = null;
  private transient Map<Long, Calendar[]> riseAndSet = null;
  private Date minDate = null, maxDate = null;
  private double minValue = Double.MAX_VALUE, maxValue = Double.MIN_VALUE;
  private final static NumberFormat VALUE_FMT = new DecimalFormat("#0.00");
  private final static DateFormat DATE_FMT = new SimpleDateFormat("dd-MMM-yyyy HH:mm Z");
//static { DATE_FMT.setTimeZone(TimeZone.getTimeZone("etc/UTC")); }
  protected transient Stroke thick = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  private boolean withSmoothing = "true".equals(System.getProperty("with.smoothing", "true"));
  private String unit = "";
  private String tz = "";
  
  private boolean withRawData = true, withSmoothData = true;

  public void setWithRawData(boolean withRawData)
  {
    this.withRawData = withRawData;
  }

  public void setWithSmoothData(boolean withSmoothData)
  {
    this.withSmoothData = withSmoothData;
  }

  private boolean mouseIsDown = false;
  private String postit = "";
  private int mouseX = 0, mouseY = 0;
  private int mouseYValue = 0;
  private float value;

  public LogAnalysisPanel(String unit)
  {
    this.unit = unit;
    try
    {
      jbInit();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  private void jbInit()
    throws Exception
  {
    this.setLayout(null);

    this.setOpaque(false);
    this.setBackground(new Color(0, 0, 0, 0));

    this.addMouseListener(this);
    this.addMouseMotionListener(this);
  }
  
  private SortedSet<Date> keys = null;
  private Map<Date, Double> smoothValue = null;
  
  private void prepareData()
  {
    if (logdata != null)
    {
      // Smooth Values
      this.smoothValue = new HashMap<Date, Double>();
      final int SMOOTH_WIDTH = 1000;
      this.keys = new TreeSet<Date>(logdata.keySet());
      Date[] keyArray = keys.toArray(new Date[keys.size()]);
      if (withSmoothing)
      {
        List<ScalarValue> ld = new ArrayList<ScalarValue>();
        for (Date d : this.keys)
          ld.add(logdata.get(d));
        
        for (int i=0; i<ld.size(); i++)
        {
          double yAccu = 0f;
          for (int acc=i-(SMOOTH_WIDTH / 2); acc<i+(SMOOTH_WIDTH/2); acc++)
          {
            double y;
            if (acc < 0)
              y = ld.get(0).getValue();
            else if (acc > (ld.size() - 1))
              y = ld.get(ld.size() - 1).getValue();
            else
              y = ld.get(acc).getValue();
            yAccu += y;
          }
          yAccu = yAccu / SMOOTH_WIDTH;
      //      System.out.println("Smooth Value:" + yAccu);
          this.smoothValue.put(keyArray[i], yAccu); 
        }
      }
      
      // Sort, mini maxi.
      this.keys = new TreeSet<Date>(logdata.keySet());
      for (Date key : this.keys) 
      { 
        ScalarValue value = logdata.get(key);
        if (minDate == null)
          minDate = key;
        else
        {
          if (key.before(minDate))
            minDate = key;
        }
        if (maxDate == null)
          maxDate = key;
        else
        {
          if (key.after(maxDate))
            maxDate = key;
        }
    //  minValue = Math.min(minValue, value.getValue());
        maxValue = Math.max(maxValue, value.getValue());
      }   
      minValue = 0;
    }
  }
  
  @Override
  protected void paintComponent(Graphics g)
  {
    super.paintComponent(g);

    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);      
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);      

    if (logdata != null)
    {
      long timespan  = maxDate.getTime() - minDate.getTime();
      double valueSpan = maxValue - minValue;
      
      g2d.setColor(Color.white);
      g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
      // Night and day
      if (this.riseAndSet != null)
      {
        Set<Long> rsK = this.riseAndSet.keySet();
        List<Date> dateList = new ArrayList<Date>();
        for (Long k : rsK)
        {
          Calendar[] calArray = this.riseAndSet.get(k);
          Date rise = calArray[0].getTime();
          Date set  = calArray[1].getTime();
          
          dateList.add(rise);
          dateList.add(set);
        }
        Color c = Color.lightGray;
        float[] cc = c.getComponents(new float[4]);
        cc[3] = 0.5f;
        g2d.setColor(new Color(cc[0], cc[1], cc[2], cc[3]));
        
        for (int d=1; d<dateList.size() - 1; d+=2)
        {
//        System.out.println("d=" + d + "/" + dateList.size());
          Date set  = dateList.get(d);
          Date rise = dateList.get(d+1);
          
          long timeoffset = set.getTime() - minDate.getTime();
          int x1 = (int)(this.getWidth() * ((float)timeoffset / (float)timespan));
      //  g2d.drawLine(x1, 0, x1, this.getHeight());
          
          timeoffset = rise.getTime() - minDate.getTime();
          int x2 = (int)(this.getWidth() * ((float)timeoffset / (float)timespan));
      //  g2d.drawLine(x1, 0, x2, this.getHeight());
          
//        System.out.println("Night from " + set.toString() + " to " + rise.toString() + "(" + x1 + ", " + x2 + ")");
          
          g2d.fillRect(x1, 0, x2 - x1, this.getHeight());
        }
      }
      // Value grid
      int minValueGrid = (int)Math.floor(minValue);
      int maxValueGrid = (int)Math.ceil(maxValue);
      g2d.setColor(Color.lightGray);
      for (int v=minValueGrid; v<maxValueGrid; v++)
      {
        double valueOffset = v - minValue;
        int y = this.getHeight() - (int)(this.getHeight() * ((float)valueOffset / (float)valueSpan));
        g2d.drawLine(0, y, this.getWidth(), y);
      }
      // Scale
      Rectangle visible = this.getVisibleRect();
      for (int v=minValueGrid; v<maxValueGrid; v++)
      {      
        double valyueOffset = v - minValue;
        int y = this.getHeight() - (int)(this.getHeight() * ((float)valyueOffset / (float)valueSpan));
        g2d.drawString(Integer.toString(v) + " " + this.unit, visible.x + 2, y - 1);
      }
      
      g2d.setColor(Color.red);
      Point previous = null;      
      // Raw Data
      if (withRawData)
      {
        for (Date key : this.keys) 
        { 
          ScalarValue value = logdata.get(key);
          Date date = key;
          double val = value.getValue();
          long timeoffset = date.getTime() - minDate.getTime();
          double valOffset = val - minValue;
          int x = (int)(this.getWidth() * ((float)timeoffset / (float)timespan));
          int y = this.getHeight() - (int)(this.getHeight() * ((float)valOffset / (float)valueSpan));
          Point current = new Point(x, y);
  //      System.out.println("x:" + x + ", y:" + y);
          if (previous != null)
            g2d.drawLine(previous.x, previous.y, current.x, current.y);
          previous = current;
        }
      }
      if (withSmoothData)
      {
        // Smooth Data
        g2d.setColor(Color.blue);
        Stroke orig = g2d.getStroke();
        g2d.setStroke(thick);
        previous = null;      
        for (Date key : this.keys) 
        {
          value = this.smoothValue.get(key).floatValue();
          long timeoffset = key.getTime() - minDate.getTime();
          double valOffset = value - minValue;
          int x = (int)(this.getWidth() * ((float)timeoffset / (float)timespan));
          int y = this.getHeight() - (int)(this.getHeight() * ((float)valOffset / (float)valueSpan));
          Point current = new Point(x, y);
        //System.out.println("x:" + x + ", y:" + y);
          if (previous != null)
            g2d.drawLine(previous.x, previous.y, current.x, current.y);
          previous = current;
        }
        g2d.setStroke(orig);
      }
      if (mouseIsDown)
      {
        g.setColor(Color.black);
        float[] dashPattern = { 2, 2, 2, 2 };
        ((Graphics2D)g).setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));
        g.drawLine(mouseX, 0, mouseX, this.getHeight());
        int y = /* this.getHeight() - */ mouseYValue;
        g.fillOval(mouseX - 2, y - 2, 4, 4);
        postit(g, postit, mouseX, mouseYValue + 20, Color.black, Color.cyan, 0.50f);
      }
    }
  }

  public void setTimeZone(String tz)
  {
    if (tz != null)
    {
      this.tz = tz;
      DATE_FMT.setTimeZone(TimeZone.getTimeZone(this.tz));      
    }
  }
  
  public void postit(Graphics g, String s, int x, int y, Color bgcolor, Color fgcolor, Float transp)
  {
    int bevel = 2;
    int postitOffset = 5;
    
    int startX = x;
    int startY = y;
    
    Color origin = g.getColor();
    g.setColor(Color.black);
    Font f = g.getFont();
    int nbCr = 0;
    int crOffset;
    for (crOffset = 0; (crOffset = s.indexOf("\n", crOffset) + 1) > 0;)
      nbCr++;

    String txt[] = new String[nbCr + 1];
    int i = 0;
    crOffset = 0;
    for (i = 0; i < nbCr; i++)
      txt[i] = s.substring(crOffset, (crOffset = s.indexOf("\n", crOffset) + 1) - 1);

    txt[i] = s.substring(crOffset);
    int strWidth = 0;
    for (i = 0; i < nbCr + 1; i++)
    {
      if (g.getFontMetrics(f).stringWidth(txt[i]) > strWidth)
        strWidth = g.getFontMetrics(f).stringWidth(txt[i]);
    }
    Color c = g.getColor(); // postitTextColor
    g.setColor(bgcolor);
    if (g instanceof Graphics2D)
    {
      // Transparency
      Graphics2D g2 = (Graphics2D)g;
      float alpha = (transp!=null?transp.floatValue():0.3f);
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    }
    // left or right, up or down...
    Point topRightExtremity      = new Point(x + postitOffset + strWidth + (2 * bevel), (y - f.getSize()) + 1);
    Point bottomRightExtremity   = new Point(x + postitOffset + strWidth + (2 * bevel), (nbCr + 1) * f.getSize());
    Point bottomLeftExtremity    = new Point(x, (nbCr + 1) * f.getSize());
    
    if (!this.getVisibleRect().contains(topRightExtremity) && !this.getVisibleRect().contains(bottomRightExtremity))   
    {
      // This display left
      startX = x - strWidth - (2 * bevel) - (2 * postitOffset);
    }
    if (!this.getVisibleRect().contains(bottomLeftExtremity))   
    {
      // This display up
  //    startY = y - ((nbCr + 1) * f.getSize());
      startY = y - ((nbCr) * f.getSize());
  //    System.out.println("Up, y [" + y + "] becomes [" + startY + "]");
    }
    
    g.fillRect(startX + postitOffset, (startY - f.getSize()) + 1, strWidth + (2 * bevel), (nbCr + 1) * f.getSize());
    if (g instanceof Graphics2D)
    {
      // Reset Transparency
      Graphics2D g2 = (Graphics2D)g;
      float alpha = 1.0f;
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    }
    if (fgcolor != null)
      g.setColor(fgcolor);
    else
      g.setColor(c);
    
    for(i = 0; i < nbCr + 1; i++)
      g.drawString(txt[i], startX + bevel + postitOffset, startY + (i * f.getSize()));

    g.setColor(origin);
  }

  public void setLogData(Map<Date, ScalarValue> logdata, Map<Long, Calendar[]> riseAndSet, String timeZone)
  {
    this.logdata = logdata;
    this.riseAndSet = riseAndSet;
    this.tz = timeZone;
    if (this.tz != null)
      DATE_FMT.setTimeZone(TimeZone.getTimeZone(this.tz));
    prepareData();
    this.repaint();
  }

  @Override
  public void mouseClicked(MouseEvent mouseEvent)
  {
    // TODO Implement this method
  }

  public void mouseDown(MouseEvent mouseEvent)
  {
    if (logdata == null || maxDate == null || minDate == null)
      return;
    if ((mouseEvent.getModifiers() & MouseEvent.BUTTON1_MASK) != 0) // Left button pressed on the graph
    {
      int x = mouseEvent.getPoint().x;
      int y = mouseEvent.getPoint().y;
      try
      {
        double valueSpan = maxValue - minValue;
        long timespan = maxDate.getTime() - minDate.getTime();
        long minTime = minDate.getTime();
        long time = minTime + (long)(timespan * ((float)x / (float)this.getWidth()));
        Date date = new Date(time);
        
        SortedSet<Date> keys = new TreeSet<Date>(logdata.keySet());
        for (Date key : keys) 
        { 
          ScalarValue value = logdata.get(key);
          double val = value.getValue();
          double valOffset = val - minValue;
          postit = VALUE_FMT.format(val) + " " + this.unit + "\n" +
                   DATE_FMT.format(date);
          mouseYValue = this.getHeight() - (int)(this.getHeight() * ((float)valOffset / (float)valueSpan));
          if (key.after(date))
            break;
        }
      }
      catch (NullPointerException npe)
      {
        npe.printStackTrace();
      }
      mouseX = x;
      mouseY = y; 
      mouseIsDown = true;
      repaint();
    }
  }

  @Override
  public void mousePressed(MouseEvent mouseEvent)
  {
    mouseDown(mouseEvent);
  }

  @Override
  public void mouseReleased(MouseEvent mouseEvent)
  {
    mouseIsDown = false;
    repaint();
  }

  @Override
  public void mouseEntered(MouseEvent mouseEvent)
  {
//  mouseIsDown = false;
  }

  @Override
  public void mouseExited(MouseEvent mouseEvent)
  {
    mouseIsDown = false;
  }

  @Override
  public void mouseDragged(MouseEvent mouseEvent)
  {
    mouseDown(mouseEvent);
  }

  @Override
  public void mouseMoved(MouseEvent mouseEvent)
  {
    int x = mouseEvent.getPoint().x;
    int y = mouseEvent.getPoint().y;
    if (logdata == null || maxDate == null || minDate == null)
      return;
    
    // Value
    try
    {
      double valSpan = maxValue - minValue;
      long timespan = maxDate.getTime() - minDate.getTime();
      double value = minValue + (valSpan * (float)(this.getHeight() - y) / (float)this.getHeight());
      long minTime = minDate.getTime();
      long time = minTime + (long)(timespan * ((float)x / (float)this.getWidth()));
      Date date = new Date(time);
      String mess = "<html><center><b>" + VALUE_FMT.format(value) + " " + this.unit + "</b><br>" +
                    DATE_FMT.format(date) + "</center></html>";
  //  System.out.println(mess);
      this.setToolTipText(mess);
    }
    catch (NullPointerException npe)
    {
      npe.printStackTrace();
    }
  }
}