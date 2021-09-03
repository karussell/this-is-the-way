package com.graphhopper.titw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.referencing.CRS;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public class Converter {
    public static void main(String[] args) throws IOException {
        new Converter().start();
    }

    private ObjectMapper om = new ObjectMapper();
    private String folder = "./";

    private void start() throws IOException {
        String url = "http://www.list.smwa.sachsen.de/gdi/download/baustelleninfo/Baustelleninfo_Sachsen_geojson.zip";
        String fileName = "sachsen.geojson/Baustelleninfo_Sperrungen_Sachsen.json";
        String output = "sachsen.tsv";
        File file = new File(folder, fileName);
        if (!file.exists()) {
            // store to disk to avoid downloading for development purposes, later we could avoid this intermediate step
            System.out.println("downloading " + file);
            write(url, file);
        }

        InputStream stream = new FileInputStream(file);
        if (fileName.endsWith(".zip"))
            stream = new ZipInputStream(stream);
        else if (fileName.endsWith(".gzip"))
            stream = new GZIPInputStream(stream);
        Map<String, Object> map = om.readValue(stream, new TypeReference<Map<String, Object>>() {
        });

        try (FileWriter writer = new FileWriter(output)) {
            CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:25833");
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326");

            MathTransform t = CRS.findMathTransform(sourceCRS, targetCRS);
            DirectPosition position = new DirectPosition2D();
            DirectPosition transformedPosition = new DirectPosition2D();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);
            List<Map> list = (List<Map>) map.get("features");
            writer.append("ID\t Type\t lat1\t lon1\t lat2\t lon2\n");
            for (Map featureObj : list) {
                Map<String, String> properties = (Map<String, String>) featureObj.get("properties");

                String kind = properties.getOrDefault("Sperrung_Art", "");
                // D = full, F = one way blocked, C = one way still possible?, B = slower
                if (kind.equals("D") || kind.equals("F") /*|| kind.equals("C")*/) {
                    String from = properties.getOrDefault("Sperrung_von", "");
                    String to = properties.getOrDefault("Sperrung_bis", "");
                    LocalDate now = LocalDate.now();
                    from = from.split(" ")[0];
                    to = to.split(" ")[0];
                    boolean blocked = !LocalDate.parse(from, formatter).isAfter(now) && !LocalDate.parse(to, formatter).isBefore(now);

                    if (!blocked)
                        continue;

                    List<List> coordinates = (List<List>) ((Map) featureObj.get("geometry")).get("coordinates");
                    String str = "";
                    for (List coordinate : coordinates) {
                        position.setOrdinate(0, ((Number) coordinate.get(0)).doubleValue());
                        position.setOrdinate(1, ((Number) coordinate.get(1)).doubleValue());
                        t.transform(position, transformedPosition);
                        // uh, it is 0=lat, 1=lon !?
                        double[] coords = transformedPosition.getCoordinate();
                        if (!str.isEmpty())
                            str += "\t ";
                        str += coords[0] + "\t " + coords[1];
                    }

                    writer.append(properties.get("ID") + "-" + properties.get("Aktenzeichen") + "\t " + kind + "\t " + str + "\n");
                }
            }

        } catch (FactoryException | TransformException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(String url, File file) throws IOException {
        OkHttpClient downloader = new OkHttpClient.Builder().
                connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).
                build();

        try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
            Response response = downloader.newCall(new Request.Builder().url(url).build()).execute();
            sink.writeAll(response.body().source());
        }
    }
}
