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
import java.util.Comparator;
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
        List<Map<String, Object>> elements = canvasElements(canvas.get("elements"));
        Map<String, Object> metadata = mapValue(canvas.get("metadata"));
        ExportBounds bounds = exportBounds(elements);
        svg.setAttribute("width", numberString(bounds.width()));
        svg.setAttribute("height", numberString(bounds.height()));
        svg.setAttribute("viewBox", numberString(bounds.minX()) + " " + numberString(bounds.minY()) + " " + numberString(bounds.width()) + " " + numberString(bounds.height()));
        doc.appendChild(svg);

        Element defs = doc.createElementNS("http://www.w3.org/2000/svg", "defs");
        appendArrowMarker(doc, defs);
        svg.appendChild(defs);

        Element background = doc.createElementNS("http://www.w3.org/2000/svg", "rect");
        background.setAttribute("x", numberString(bounds.minX()));
        background.setAttribute("y", numberString(bounds.minY()));
        background.setAttribute("width", numberString(bounds.width()));
        background.setAttribute("height", numberString(bounds.height()));
        background.setAttribute("fill", stringValue(metadata.get("backgroundColor"), "#eef2f5"));
        svg.appendChild(background);

        if (booleanValue(metadata.get("gridEnabled"), false)) {
            appendGrid(doc, svg, bounds);
        }

        elements.stream()
            .sorted(Comparator.comparingDouble(element -> doubleValue(element.get("zIndex"), 0)))
            .map(element -> renderElement(doc, element))
            .forEach(svg::appendChild);

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

    private record ExportBounds(double minX, double minY, double width, double height) {}

    private record ElementBounds(double minX, double minY, double maxX, double maxY) {}

    private record SvgPoint(double x, double y) {}

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

    private List<Map<String, Object>> canvasElements(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .map(this::mapValue)
            .filter(element -> !element.isEmpty())
            .toList();
    }

    private ExportBounds exportBounds(List<Map<String, Object>> elements) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Map<String, Object> element : elements) {
            ElementBounds bounds = elementBounds(element);
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return new ExportBounds(0, 0, 800, 600);
        }

        double contentWidth = Math.max(1, maxX - minX);
        double contentHeight = Math.max(1, maxY - minY);
        double paddingX = contentWidth * 0.1;
        double paddingY = contentHeight * 0.1;
        return new ExportBounds(minX - paddingX, minY - paddingY, contentWidth + paddingX * 2, contentHeight + paddingY * 2);
    }

    private ElementBounds elementBounds(Map<String, Object> element) {
        String type = stringValue(element.get("type"), "rectangle");
        Map<String, Object> data = mapValue(element.get("data"));

        if ("connector".equals(type) || "arrow".equals(type) || "curve".equals(type)) {
            List<SvgPoint> points = curvePoints(element, data);
            double minX = points.stream().mapToDouble(SvgPoint::x).min().orElse(0);
            double minY = points.stream().mapToDouble(SvgPoint::y).min().orElse(0);
            double maxX = points.stream().mapToDouble(SvgPoint::x).max().orElse(0);
            double maxY = points.stream().mapToDouble(SvgPoint::y).max().orElse(0);
            return new ElementBounds(minX, minY, maxX, maxY);
        }

        if ("freehand".equals(type)) {
            List<SvgPoint> points = pointList(data.get("points"));
            if (!points.isEmpty()) {
                double minX = points.stream().mapToDouble(SvgPoint::x).min().orElse(0);
                double minY = points.stream().mapToDouble(SvgPoint::y).min().orElse(0);
                double maxX = points.stream().mapToDouble(SvgPoint::x).max().orElse(0);
                double maxY = points.stream().mapToDouble(SvgPoint::y).max().orElse(0);
                return new ElementBounds(minX, minY, maxX, maxY);
            }
        }

        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = doubleValue(element.get("width"), 0);
        double height = doubleValue(element.get("height"), 0);
        return new ElementBounds(x, y, x + Math.max(1, width), y + Math.max(1, height));
    }

    private Element renderElement(Document doc, Map<String, Object> element) {
        Element group = doc.createElementNS("http://www.w3.org/2000/svg", "g");
        applyRotation(group, element);
        String type = stringValue(element.get("type"), "rectangle");

        if ("circle".equals(type)) {
            renderCircle(doc, group, element);
        } else if ("text".equals(type)) {
            renderText(doc, group, element);
        } else if ("image".equals(type)) {
            renderImage(doc, group, element);
        } else if ("connector".equals(type) || "arrow".equals(type) || "curve".equals(type)) {
            renderCurve(doc, group, element);
        } else if ("freehand".equals(type)) {
            renderFreehand(doc, group, element);
        } else {
            renderRectangle(doc, group, element);
        }

        return group;
    }

    private void renderRectangle(Document doc, Element group, Map<String, Object> element) {
        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = Math.max(1, doubleValue(element.get("width"), 120));
        double height = Math.max(1, doubleValue(element.get("height"), 60));
        Element rect = doc.createElementNS("http://www.w3.org/2000/svg", "rect");
        rect.setAttribute("x", numberString(x));
        rect.setAttribute("y", numberString(y));
        rect.setAttribute("width", numberString(width));
        rect.setAttribute("height", numberString(height));
        rect.setAttribute("rx", "8");
        applyShapeStyle(rect, element);
        group.appendChild(rect);
        appendCenteredText(doc, group, elementText(element, ""), x + width / 2, y + height / 2, Math.max(1, width - 16));
    }

    private void renderCircle(Document doc, Element group, Map<String, Object> element) {
        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = Math.max(1, doubleValue(element.get("width"), 120));
        double height = Math.max(1, doubleValue(element.get("height"), 120));
        Element ellipse = doc.createElementNS("http://www.w3.org/2000/svg", "ellipse");
        ellipse.setAttribute("cx", numberString(x + width / 2));
        ellipse.setAttribute("cy", numberString(y + height / 2));
        ellipse.setAttribute("rx", numberString(width / 2));
        ellipse.setAttribute("ry", numberString(height / 2));
        applyShapeStyle(ellipse, element);
        group.appendChild(ellipse);
        appendCenteredText(doc, group, elementText(element, ""), x + width / 2, y + height / 2, Math.max(1, width - 16));
    }

    private void renderText(Document doc, Element group, Map<String, Object> element) {
        Map<String, Object> style = mapValue(element.get("style"));
        String text = elementText(element, "");
        if (text.isBlank()) {
            return;
        }
        Element textElement = doc.createElementNS("http://www.w3.org/2000/svg", "text");
        textElement.setAttribute("x", numberString(doubleValue(element.get("x"), 0)));
        textElement.setAttribute("y", numberString(doubleValue(element.get("y"), 0) + 8));
        textElement.setAttribute("fill", textColor(style));
        textElement.setAttribute("font-family", stringValue(style.get("fontFamily"), "Inter"));
        textElement.setAttribute("font-size", numberString(doubleValue(style.get("fontSize"), 18)));
        textElement.setAttribute("font-weight", String.valueOf(style.getOrDefault("fontWeight", "700")));
        textElement.setAttribute("dominant-baseline", "hanging");
        applyOpacity(textElement, style);
        appendTextLines(doc, textElement, text, doubleValue(element.get("x"), 0), doubleValue(style.get("fontSize"), 18) * 1.45);
        group.appendChild(textElement);
    }

    private void renderImage(Document doc, Element group, Map<String, Object> element) {
        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = Math.max(1, doubleValue(element.get("width"), 160));
        double height = Math.max(1, doubleValue(element.get("height"), 96));
        Element rect = doc.createElementNS("http://www.w3.org/2000/svg", "rect");
        rect.setAttribute("x", numberString(x));
        rect.setAttribute("y", numberString(y));
        rect.setAttribute("width", numberString(width));
        rect.setAttribute("height", numberString(height));
        rect.setAttribute("rx", "8");
        applyShapeStyle(rect, element);
        group.appendChild(rect);

        String src = stringValue(mapValue(element.get("data")).get("src"), "");
        if (!src.isBlank()) {
            Element image = doc.createElementNS("http://www.w3.org/2000/svg", "image");
            image.setAttribute("x", numberString(x));
            image.setAttribute("y", numberString(y));
            image.setAttribute("width", numberString(width));
            image.setAttribute("height", numberString(height));
            image.setAttribute("href", src);
            image.setAttribute("preserveAspectRatio", "xMidYMid meet");
            group.appendChild(image);
            return;
        }

        Element path = doc.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", "M " + numberString(x + 20) + " " + numberString(y + height - 24) + " L " + numberString(x + 72) + " " + numberString(y + height - 72) + " L " + numberString(x + 112) + " " + numberString(y + height - 32));
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", "#8a5cff");
        path.setAttribute("stroke-width", "4");
        path.setAttribute("stroke-linecap", "round");
        path.setAttribute("stroke-linejoin", "round");
        group.appendChild(path);
        appendTopText(doc, group, elementText(element, "Image placeholder"), x + 18, y + 18, 14, Math.max(1, width - 36));
    }

    private void renderCurve(Document doc, Element group, Map<String, Object> element) {
        Map<String, Object> data = mapValue(element.get("data"));
        List<SvgPoint> points = curvePoints(element, data);
        SvgPoint start = points.get(0);
        SvgPoint controlStart = points.get(1);
        SvgPoint controlEnd = points.get(2);
        SvgPoint end = points.get(3);
        String d = "M " + numberString(start.x()) + " " + numberString(start.y()) + " C " + numberString(controlStart.x()) + " " + numberString(controlStart.y()) + " " + numberString(controlEnd.x()) + " " + numberString(controlEnd.y()) + " " + numberString(end.x()) + " " + numberString(end.y());
        String type = stringValue(element.get("type"), "");
        String preset = stringValue(data.get("curvePreset"), "curve".equals(type) ? "dashed" : "solid");

        if ("signal".equals(preset) || "signalReverse".equals(preset)) {
            Element basePath = doc.createElementNS("http://www.w3.org/2000/svg", "path");
            basePath.setAttribute("d", d);
            applyLineStyle(basePath, element);
            basePath.setAttribute("stroke-opacity", "0.24");
            group.appendChild(basePath);
        }

        Element path = doc.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", d);
        applyLineStyle(path, element);
        if ("arrow".equals(type)) {
            path.setAttribute("marker-end", "url(#canvas-arrow-head)");
        }
        applyCurvePreset(doc, path, preset);
        group.appendChild(path);
    }

    private void applyCurvePreset(Document doc, Element path, String preset) {
        if ("solid".equals(preset)) {
            return;
        }
        if ("dotted".equals(preset)) {
            path.setAttribute("stroke-dasharray", "2 8");
            return;
        }
        if ("pulse".equals(preset) || "pulseReverse".equals(preset) || "signal".equals(preset) || "signalReverse".equals(preset)) {
            boolean reverse = "pulseReverse".equals(preset) || "signalReverse".equals(preset);
            path.setAttribute("stroke-dasharray", "30 240");
            Element animate = doc.createElementNS("http://www.w3.org/2000/svg", "animate");
            animate.setAttribute("attributeName", "stroke-dashoffset");
            animate.setAttribute("from", "0");
            animate.setAttribute("to", reverse ? "270" : "-270");
            animate.setAttribute("dur", "1.8s");
            animate.setAttribute("repeatCount", "indefinite");
            path.appendChild(animate);
            return;
        }
        path.setAttribute("stroke-dasharray", "7 8");
    }

    private void renderFreehand(Document doc, Element group, Map<String, Object> element) {
        List<SvgPoint> points = pointList(mapValue(element.get("data")).get("points"));
        if (points.isEmpty()) {
            return;
        }
        Element polyline = doc.createElementNS("http://www.w3.org/2000/svg", "polyline");
        polyline.setAttribute("points", points.stream().map(point -> numberString(point.x()) + "," + numberString(point.y())).reduce((left, right) -> left + " " + right).orElse(""));
        applyLineStyle(polyline, element);
        group.appendChild(polyline);
    }

    private void applyShapeStyle(Element target, Map<String, Object> element) {
        Map<String, Object> style = mapValue(element.get("style"));
        target.setAttribute("fill", stringValue(style.get("fill"), "#ffffff"));
        target.setAttribute("stroke", stringValue(style.get("stroke"), "#273142"));
        target.setAttribute("stroke-width", numberString(exportStrokeWidth(element, 2)));
        applyOpacity(target, style);
    }

    private void applyLineStyle(Element target, Map<String, Object> element) {
        Map<String, Object> style = mapValue(element.get("style"));
        target.setAttribute("fill", "none");
        target.setAttribute("stroke", stringValue(style.get("stroke"), "#273142"));
        target.setAttribute("stroke-width", numberString(exportStrokeWidth(element, 2)));
        target.setAttribute("stroke-linecap", "round");
        target.setAttribute("stroke-linejoin", "round");
        applyOpacity(target, style);
    }

    private double exportStrokeWidth(Map<String, Object> element, double fallback) {
        String type = stringValue(element.get("type"), "");
        double width = doubleValue(mapValue(element.get("style")).get("strokeWidth"), fallback);
        if ("freehand".equals(type)) {
            return width;
        }
        return width > 0 ? Math.max(2, width) : 0;
    }

    private void applyOpacity(Element target, Map<String, Object> style) {
        double opacity = doubleValue(style.get("opacity"), 1);
        if (opacity < 1) {
            target.setAttribute("opacity", numberString(opacity));
        }
    }

    private void applyRotation(Element group, Map<String, Object> element) {
        double rotation = doubleValue(element.get("rotation"), 0);
        if (rotation == 0) {
            return;
        }
        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = doubleValue(element.get("width"), 0);
        double height = doubleValue(element.get("height"), 0);
        group.setAttribute("transform", "rotate(" + numberString(rotation) + " " + numberString(x + width / 2) + " " + numberString(y + height / 2) + ")");
    }

    private void appendCenteredText(Document doc, Element group, String text, double x, double y, double maxWidth) {
        if (text == null || text.isBlank()) {
            return;
        }
        Element textElement = doc.createElementNS("http://www.w3.org/2000/svg", "text");
        textElement.setAttribute("x", numberString(x));
        textElement.setAttribute("y", numberString(y));
        textElement.setAttribute("fill", "#273142");
        textElement.setAttribute("font-family", "Inter");
        textElement.setAttribute("font-size", "15");
        textElement.setAttribute("font-weight", "700");
        textElement.setAttribute("text-anchor", "middle");
        textElement.setAttribute("dominant-baseline", "middle");
        appendTextLines(doc, textElement, text, x, 21.75, maxWidth, true);
        group.appendChild(textElement);
    }

    private void appendTopText(Document doc, Element group, String text, double x, double y, double fontSize, double maxWidth) {
        if (text == null || text.isBlank()) {
            return;
        }
        Element textElement = doc.createElementNS("http://www.w3.org/2000/svg", "text");
        textElement.setAttribute("x", numberString(x));
        textElement.setAttribute("y", numberString(y));
        textElement.setAttribute("fill", "#273142");
        textElement.setAttribute("font-family", "Inter");
        textElement.setAttribute("font-size", numberString(fontSize));
        textElement.setAttribute("font-weight", "700");
        textElement.setAttribute("dominant-baseline", "hanging");
        appendTextLines(doc, textElement, text, x, fontSize * 1.45, maxWidth, false);
        group.appendChild(textElement);
    }

    private void appendTextLines(Document doc, Element textElement, String text, double x, double lineHeight) {
        appendTextLines(doc, textElement, text, x, lineHeight, 0, false);
    }

    private void appendTextLines(Document doc, Element textElement, String text, double x, double lineHeight, double maxWidth, boolean centered) {
        String[] lines = text.split("\\R", -1);
        double firstDy = centered && lines.length > 1 ? -((lines.length - 1) * lineHeight / 2) : 0;
        for (int i = 0; i < lines.length; i++) {
            Element tspan = doc.createElementNS("http://www.w3.org/2000/svg", "tspan");
            tspan.setAttribute("x", numberString(x));
            tspan.setAttribute("dy", i == 0 ? numberString(firstDy) : numberString(lineHeight));
            if (maxWidth > 0) {
                tspan.setAttribute("textLength", numberString(Math.min(maxWidth, Math.max(1, lines[i].length() * 8.5))));
                tspan.setAttribute("lengthAdjust", "spacingAndGlyphs");
            }
            tspan.setTextContent(lines[i]);
            textElement.appendChild(tspan);
        }
    }

    private void appendGrid(Document doc, Element svg, ExportBounds bounds) {
        Element grid = doc.createElementNS("http://www.w3.org/2000/svg", "g");
        grid.setAttribute("stroke", "#4d7cfe");
        grid.setAttribute("stroke-opacity", "0.22");
        grid.setAttribute("stroke-width", "1");
        double startX = Math.ceil(bounds.minX() / 32) * 32;
        double endX = bounds.minX() + bounds.width();
        double startY = Math.ceil(bounds.minY() / 32) * 32;
        double endY = bounds.minY() + bounds.height();

        for (double x = startX; x <= endX; x += 32) {
            Element line = doc.createElementNS("http://www.w3.org/2000/svg", "line");
            line.setAttribute("x1", numberString(x));
            line.setAttribute("y1", numberString(bounds.minY()));
            line.setAttribute("x2", numberString(x));
            line.setAttribute("y2", numberString(endY));
            grid.appendChild(line);
        }

        for (double y = startY; y <= endY; y += 32) {
            Element line = doc.createElementNS("http://www.w3.org/2000/svg", "line");
            line.setAttribute("x1", numberString(bounds.minX()));
            line.setAttribute("y1", numberString(y));
            line.setAttribute("x2", numberString(endX));
            line.setAttribute("y2", numberString(y));
            grid.appendChild(line);
        }

        svg.appendChild(grid);
    }

    private void appendArrowMarker(Document doc, Element defs) {
        Element marker = doc.createElementNS("http://www.w3.org/2000/svg", "marker");
        marker.setAttribute("id", "canvas-arrow-head");
        marker.setAttribute("markerWidth", "12");
        marker.setAttribute("markerHeight", "12");
        marker.setAttribute("refX", "10");
        marker.setAttribute("refY", "6");
        marker.setAttribute("orient", "auto");
        marker.setAttribute("markerUnits", "strokeWidth");
        Element path = doc.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", "M 0 0 L 12 6 L 0 12 z");
        path.setAttribute("fill", "#273142");
        marker.appendChild(path);
        defs.appendChild(marker);
    }

    private String elementText(Map<String, Object> element, String fallback) {
        Map<String, Object> data = mapValue(element.get("data"));
        String text = stringValue(data.get("text"), "");
        if (!text.isBlank()) {
            return text;
        }
        text = stringValue(element.get("content"), "");
        if (!text.isBlank()) {
            return text;
        }
        return stringValue(element.get("label"), fallback);
    }

    private String textColor(Map<String, Object> style) {
        String stroke = stringValue(style.get("stroke"), "#273142");
        return "transparent".equals(stroke) ? "#273142" : stroke;
    }

    private SvgPoint pointValue(Object value, SvgPoint fallback) {
        Map<String, Object> point = mapValue(value);
        if (point.isEmpty()) {
            return fallback;
        }
        return new SvgPoint(doubleValue(point.get("x"), fallback.x()), doubleValue(point.get("y"), fallback.y()));
    }

    private List<SvgPoint> curvePoints(Map<String, Object> element, Map<String, Object> data) {
        String type = stringValue(element.get("type"), "");
        double x = doubleValue(element.get("x"), 0);
        double y = doubleValue(element.get("y"), 0);
        double width = Math.max(1, doubleValue(element.get("width"), 180));
        double height = Math.max(1, doubleValue(element.get("height"), 80));
        String mode = stringValue(data.get("lineMode"), "curve".equals(type) ? "curve" : "line");
        SvgPoint defaultStart = "curve".equals(type) ? new SvgPoint(x, y + height * 0.7) : new SvgPoint(x, y);
        SvgPoint defaultEnd = "curve".equals(type) ? new SvgPoint(x + width, y + height * 0.35) : new SvgPoint(x + width, y + height);
        SvgPoint start = pointValue(data.get("start"), defaultStart);
        SvgPoint end = pointValue(data.get("end"), defaultEnd);
        SvgPoint controlStart = "curve".equals(mode) ? pointValue(data.get("controlStart"), straightControlPoint(start, end, 1.0 / 3.0)) : straightControlPoint(start, end, 1.0 / 3.0);
        SvgPoint controlEnd = "curve".equals(mode) ? pointValue(data.get("controlEnd"), straightControlPoint(start, end, 2.0 / 3.0)) : straightControlPoint(start, end, 2.0 / 3.0);
        return List.of(start, controlStart, controlEnd, end);
    }

    private SvgPoint straightControlPoint(SvgPoint start, SvgPoint end, double ratio) {
        return new SvgPoint(start.x() + (end.x() - start.x()) * ratio, start.y() + (end.y() - start.y()) * ratio);
    }

    private List<SvgPoint> pointList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .map(point -> pointValue(point, new SvgPoint(0, 0)))
            .toList();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, mapValue);
            }
        });
        return result;
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private String numberString(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(Math.round(value * 1000.0) / 1000.0);
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
