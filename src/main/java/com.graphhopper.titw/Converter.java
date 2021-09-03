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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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
        File file = new File(folder, fileName);
        if (!file.exists()) {
            // store to disk to avoid downloading for development purposes, later we could avoid this intermediate step
            System.out.println("downloading " + file);
            write(url, file);
        }

        System.out.println("converting ...");
        InputStream stream = new FileInputStream(file);
        if (fileName.endsWith(".zip"))
            stream = new ZipInputStream(stream);
        else if (fileName.endsWith(".gzip"))
            stream = new GZIPInputStream(stream);
        Map<String, Object> map = om.readValue(stream, new TypeReference<Map<String, Object>>() {
        });
        System.out.println(map);

        try {
            CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:25833");
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");
            MathTransform t = CRS.findMathTransform(sourceCRS, targetCRS);
            DirectPosition position = new DirectPosition2D();
            DirectPosition transformedPosition = new DirectPosition2D();

            position.setOrdinate(0, 447239.96);
            position.setOrdinate(1, 5698938.2199999997);
            t.transform(position, transformedPosition);
            System.out.println(Arrays.toString(transformedPosition.getCoordinate()));
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
