package mindustry.plugin.utils.plot;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;


public class PlotTest2 {
    public static void main(String[] args) {
//        XYSeries series = new XYSeries("Series ");
//        for (int i = 0; i < 100; i++) {
//            series.add(i, i * i);
//        }
//        livePlot(series, "Test");
        double[] X = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        double[] Y = {0, 1, 4, 9, 16, 25, 36, 49, 64, 81};
        savePlot(X, Y);
    }

    public static String savePlot(double[] X, String fileName, String title) {
        Double[] X2 = new Double[X.length];
        for (int i = 0; i < X.length; i++) {
            X2[i] = X[i];
        }
        return savePlot(X2, fileName, title);
    }

    public static String savePlot(long[] X, String fileName, String title) {
        Double[] X2 = new Double[X.length];
        for (int i = 0; i < X.length; i++) {
            X2[i] = (double) X[i];
        }
        return savePlot(X2, fileName, title);
    }

    public static String savePlot(int[] X, String fileName, String title) {
        Double[] X2 = new Double[X.length];
        for (int i = 0; i < X.length; i++) {
            X2[i] = (double) X[i];
        }
        return savePlot(X2, fileName, title);
    }

    public static <T> String savePlot(T[] data, String fileName, String title) {
        XYSeries series = new XYSeries(title);
        for (int i = 0; i < data.length; i++) {
            series.add(i, (double) data[i]);
        }
        ArrayList<XYSeries> seriesList = new ArrayList<>();
        seriesList.add(series);
        return livePlot(seriesList, title, fileName);
    }

    public static String savePlot(double[] tps, double[] memoryUsage) {
        ArrayList<XYSeries> series = new ArrayList<>();
        XYSeries tpsSeries = new XYSeries("TPS");
        XYSeries memorySeries = new XYSeries("Memory Usage");
        for (int i = 0; i < tps.length; i++) {
            tpsSeries.add(i, tps[i]);
            memorySeries.add(i, memoryUsage[i]);
        }
        series.add(tpsSeries);
        series.add(memorySeries);
        return livePlot(series, "Test Data", "test_data");
    }

    public static String livePlot(ArrayList<XYSeries> s, String title, String fileName) {
        final XYSeriesCollection data = new XYSeriesCollection();
        for (XYSeries series : s) {
            data.addSeries(series);
        }
        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "seconds",
                title,
                data,
                PlotOrientation.VERTICAL,
                true,
                true, true);
        final ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(1500, 700));
        BufferedImage buff = chart.createBufferedImage(1500, 700);
        return saveBufferedImage(buff, fileName, "png").getAbsolutePath();
    }

    public static File saveBufferedImage(BufferedImage image, String fileName, String format) {
        File file = null;
        try {
            // Save as PNG
            file = new File("renders/" + fileName + "." + format);
            ImageIO.write(image, format, file);
            System.out.println("Saved as " + file.getAbsolutePath());

//            // Save as JPEG
//            file = new File("renders/" + fileName + ".jpg");
//            ImageIO.write(image, "jpg", file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }
}
