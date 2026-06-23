package io.livelattice.importexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.dto.DashboardExportData;
import io.livelattice.importexport.exception.UnsupportedFormatException;
import io.livelattice.importexport.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class FormatTransformer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentBuilderFactory documentBuilderFactory;

    public FormatTransformer() {
        this.documentBuilderFactory = documentBuilderFactory();
    }

    public Map<String, Object> importFile(MultipartFile file, String title) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new ValidationException("Uploaded file is empty");
        }
        String detected = detectContentType(bytes);
        return switch (detected) {
            case "image/svg+xml" -> svgToCanvas(bytes, title);
            case "application/drawio" -> drawioToCanvas(bytes, title);
            case "application/json" -> jsonToCanvas(bytes, title);
            default -> throw new UnsupportedFormatException("Unsupported content type: " + detected);
        };
    }

    public String detectContentType(byte[] bytes) {
        if (bytes.length == 0) {
            return "application/octet-stream";
        }
        String text = new String(bytes, 0, Math.min(512, bytes.length), StandardCharsets.UTF_8);
        String trimmed = text.trim().toLowerCase();
        if (trimmed.startsWith("{\"mxfile") || trimmed.contains("\"mxfile")) {
            return "application/drawio";
        }
        if (trimmed.startsWith("\u003c?xml") || trimmed.startsWith("\u003csvg") || trimmed.contains("\u003csvg")) {
            if (text.contains("mxGraphModel") || text.contains("mxfile") || text.contains("\u003cdiagram")) {
                return "application/drawio";
            }
            return "image/svg+xml";
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        }
        if (text.contains("mxGraphModel") || text.contains("mxfile")) {
            return "application/drawio";
        }
        return "application/octet-stream";
    }

    public Map<String, Object> svgToCanvas(byte[] bytes, String title) {
        Map<String, Object> canvas = baseCanvas(title);
        List<Map<String, Object>> elements = new ArrayList<>();
        try {
            Document doc = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            NodeList rects = doc.getElementsByTagName("rect");
            for (int i = 0; i < rects.getLength(); i++) {
                Element el = (Element) rects.item(i);
                elements.add(elementMap("rectangle", attr(el, "x", 0.0), attr(el, "y", 0.0), attr(el, "width", 100.0), attr(el, "height", 50.0)));
            }
            NodeList circles = doc.getElementsByTagName("circle");
            for (int i = 0; i < circles.getLength(); i++) {
                Element el = (Element) circles.item(i);
                elements.add(elementMap("circle", attr(el, "cx", 0.0), attr(el, "cy", 0.0), attr(el, "r", 25.0), null));
            }
            NodeList ellipses = doc.getElementsByTagName("ellipse");
            for (int i = 0; i < ellipses.getLength(); i++) {
                Element el = (Element) ellipses.item(i);
                elements.add(elementMap("ellipse", attr(el, "cx", 0.0), attr(el, "cy", 0.0), attr(el, "rx", 50.0), attr(el, "ry", 25.0)));
            }
            NodeList texts = doc.getElementsByTagName("text");
            for (int i = 0; i < texts.getLength(); i++) {
                Element el = (Element) texts.item(i);
                Map<String, Object> text = elementMap("text", attr(el, "x", 0.0), attr(el, "y", 0.0), null, null);
                text.put("content", el.getTextContent());
                elements.add(text);
            }
        } catch (Exception e) {
            throw new ValidationException("Failed to parse SVG: " + e.getMessage());
        }
        canvas.put("elements", elements);
        return canvas;
    }

    public Map<String, Object> drawioToCanvas(byte[] bytes, String title) {
        Map<String, Object> canvas = baseCanvas(title);
        List<Map<String, Object>> elements = new ArrayList<>();
        try {
            Document doc = documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            NodeList mxCells = doc.getElementsByTagName("mxCell");
            for (int i = 0; i < mxCells.getLength(); i++) {
                Element cell = (Element) mxCells.item(i);
                if (cell.hasAttribute("vertex") || cell.hasAttribute("edge")) {
                    String style = cell.getAttribute("style");
                    String value = cell.getAttribute("value");
                    String type = "rectangle";
                    if (style != null && !style.isEmpty()) {
                        if (style.contains("ellipse")) {
                            type = "ellipse";
                        } else if (style.contains("rhombus")) {
                            type = "diamond";
                        } else if (style.contains("rounded")) {
                            type = "rounded";
                        }
                    }
                    Geometry geometry = resolveGeometry(cell);
                    Map<String, Object> element = elementMap(type, geometry.x, geometry.y, geometry.width, geometry.height);
                    if (value != null && !value.isEmpty()) {
                        element.put("label", value);
                    }
                    elements.add(element);
                }
            }
        } catch (Exception e) {
            throw new ValidationException("Failed to parse draw.io XML: " + e.getMessage());
        }
        canvas.put("elements", elements);
        return canvas;
    }

    public Map<String, Object> jsonToCanvas(byte[] bytes, String title) throws IOException {
        return objectMapper.readValue(bytes, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    public byte[] exportCanvasToSvg(Map<String, Object> canvas) throws Exception {
        Document doc = documentBuilderFactory.newDocumentBuilder().newDocument();
        Element svg = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
        svg.setAttribute("width", "800");
        svg.setAttribute("height", "600");
        svg.setAttribute("viewBox", "0 0 800 600");
        doc.appendChild(svg);
        List<Map<String, Object>> elements = (List<Map<String, Object>>) canvas.get("elements");
        if (elements != null) {
            for (Map<String, Object> element : elements) {
                svg.appendChild(renderElement(doc, element));
            }
        }
        StringWriter writer = new StringWriter();
        transformerFactory().newTransformer().transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportCanvasToPng(Map<String, Object> canvas) throws Exception {
        byte[] svgBytes = exportCanvasToSvg(canvas);
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 0.0847f);
        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgBytes));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TranscoderOutput transOutput = new TranscoderOutput(output);
        transcoder.transcode(input, transOutput);
        return output.toByteArray();
    }

    public byte[] exportCanvasToPdf(Map<String, Object> canvas) throws Exception {
        byte[] svgBytes = exportCanvasToSvg(canvas);
        PDFTranscoder transcoder = new PDFTranscoder();
        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgBytes));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TranscoderOutput transOutput = new TranscoderOutput(output);
        transcoder.transcode(input, transOutput);
        return output.toByteArray();
    }

    public byte[] dashboardToCsv(DashboardExportData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (data.rows().isEmpty()) {
            output.write("dashboard_id,title\n".getBytes(StandardCharsets.UTF_8));
            output.write((data.dashboardId() + "," + data.title() + "\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }
        List<String> columns = new ArrayList<>(data.rows().get(0).keySet());
        String header = String.join(",", columns) + "\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        for (Map<String, Object> row : data.rows()) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                values.add(value == null ? "" : escapeCsv(String.valueOf(value)));
            }
            output.write((String.join(",", values) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    public byte[] dashboardToXlsx(DashboardExportData data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Dashboard");
            if (data.rows().isEmpty()) {
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("dashboard_id");
                row.createCell(1).setCellValue("title");
                Row dataRow = sheet.createRow(1);
                dataRow.createCell(0).setCellValue(data.dashboardId());
                dataRow.createCell(1).setCellValue(data.title());
            } else {
                List<String> columns = new ArrayList<>(data.rows().get(0).keySet());
                Row header = sheet.createRow(0);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(columns.get(i));
                }
                for (int i = 0; i < data.rows().size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    Map<String, Object> dataRow = data.rows().get(i);
                    for (int j = 0; j < columns.size(); j++) {
                        Cell cell = row.createCell(j);
                        Object value = dataRow.get(columns.get(j));
                        if (value == null) {
                            cell.setBlank();
                        } else {
                            cell.setCellValue(String.valueOf(value));
                        }
                    }
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] dashboardToJson(DashboardExportData data) throws IOException {
        return objectMapper.writeValueAsBytes(data);
    }

    private Geometry resolveGeometry(Element cell) {
        NodeList geometries = cell.getElementsByTagName("mxGeometry");
        if (geometries.getLength() > 0) {
            Element geometry = (Element) geometries.item(0);
            double x = attr(geometry, "x", 0.0);
            double y = attr(geometry, "y", 0.0);
            double width = attr(geometry, "width", 120.0);
            double height = attr(geometry, "height", 60.0);
            return new Geometry(x, y, width, height);
        }
        double x = attr(cell, "x", 0.0);
        double y = attr(cell, "y", 0.0);
        double width = attr(cell, "width", 120.0);
        double height = attr(cell, "height", 60.0);
        return new Geometry(x, y, width, height);
    }

    private record Geometry(double x, double y, double width, double height) {}

    private DocumentBuilderFactory documentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException | IllegalArgumentException e) {
            throw new IllegalStateException("XML parser does not support required security features", e);
        }
        return factory;
    }

    private TransformerFactory transformerFactory() throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory;
    }

    private Map<String, Object> baseCanvas(String title) {
        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("id", UUID.randomUUID().toString());
        canvas.put("title", title);
        canvas.put("version", 1);
        canvas.put("elements", new ArrayList<Map<String, Object>>());
        return canvas;
    }

    private Map<String, Object> elementMap(String type, Double x, Double y, Double width, Double height) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", type);
        if (x != null) {
            element.put("x", x);
        }
        if (y != null) {
            element.put("y", y);
        }
        if (width != null) {
            element.put("width", width);
        }
        if (height != null) {
            element.put("height", height);
        }
        return element;
    }

    private double attr(Element element, String name, double defaultValue) {
        String value = element.getAttribute(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Element renderElement(Document doc, Map<String, Object> element) {
        String type = String.valueOf(element.get("type"));
        double x = doubleValue(element.get("x"));
        double y = doubleValue(element.get("y"));
        double width = doubleValue(element.get("width"));
        double height = doubleValue(element.get("height"));
        Element result;
        switch (type) {
            case "circle":
                result = doc.createElementNS("http://www.w3.org/2000/svg", "circle");
                result.setAttribute("cx", String.valueOf(x));
                result.setAttribute("cy", String.valueOf(y));
                result.setAttribute("r", String.valueOf(width));
                break;
            case "ellipse":
                result = doc.createElementNS("http://www.w3.org/2000/svg", "ellipse");
                result.setAttribute("cx", String.valueOf(x));
                result.setAttribute("cy", String.valueOf(y));
                result.setAttribute("rx", String.valueOf(width));
                result.setAttribute("ry", String.valueOf(height));
                break;
            case "text":
                result = doc.createElementNS("http://www.w3.org/2000/svg", "text");
                result.setAttribute("x", String.valueOf(x));
                result.setAttribute("y", String.valueOf(y));
                result.setTextContent(String.valueOf(element.get("content")));
                break;
            default:
                result = doc.createElementNS("http://www.w3.org/2000/svg", "rect");
                result.setAttribute("x", String.valueOf(x));
                result.setAttribute("y", String.valueOf(y));
                result.setAttribute("width", String.valueOf(width));
                result.setAttribute("height", String.valueOf(height));
                result.setAttribute("fill", "#ffffff");
                result.setAttribute("stroke", "#000000");
        }
        return result;
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
