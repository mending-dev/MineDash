package dev.mending.minedash.core.module;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the SVG icon of a module into markup that is safe to put into the page.
 *
 * <p>The icon is parsed and written again from a list of allowed elements and attributes. Scripts,
 * styles, images, links, event handlers and references to other files never get through, whatever
 * the module author wrote. The ids inside the icon get a prefix, so icons of different modules
 * cannot interfere with each other.
 */
final class SvgIcon {

    /** Largest icon in characters. */
    static final int MAX_CHARS = 32 * 1024;

    private static final int MAX_ELEMENTS = 200;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_VALUE_CHARS = 16 * 1024;

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static final String DEFAULT_VIEW_BOX = "0 0 24 24";

    private static final Set<String> ELEMENTS = new HashSet<>(Arrays.asList(
            "svg", "g", "defs", "path", "circle", "ellipse", "rect", "line", "polyline", "polygon",
            "linearGradient", "radialGradient", "stop", "clipPath", "mask"));

    private static final Set<String> ATTRIBUTES = new HashSet<>(Arrays.asList(
            "id", "href",
            "d", "points", "cx", "cy", "r", "rx", "ry", "x", "y", "x1", "y1", "x2", "y2", "fx", "fy",
            "width", "height", "transform",
            "fill", "fill-opacity", "fill-rule", "clip-rule", "opacity",
            "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
            "stroke-dasharray", "stroke-dashoffset", "stroke-opacity",
            "clip-path", "mask", "clipPathUnits", "maskUnits", "maskContentUnits",
            "offset", "stop-color", "stop-opacity", "gradientUnits", "gradientTransform", "spreadMethod"));

    /** The size, position and id of the outer element are set by the panel, not by the icon. */
    private static final Set<String> ROOT_IGNORED = new HashSet<>(Arrays.asList(
            "id", "href", "x", "y", "width", "height"));

    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Pattern REFERENCE = Pattern.compile("url\\(\\s*#([A-Za-z][A-Za-z0-9_-]{0,63})\\s*\\)");
    private static final Pattern VIEW_BOX = Pattern.compile(
            "\\s*-?\\d+(?:\\.\\d+)?(?:[\\s,]+-?\\d+(?:\\.\\d+)?){3}\\s*");

    private final String idPrefix;
    private final StringBuilder out = new StringBuilder();
    private int elements;

    private SvgIcon(String idPrefix) {
        this.idPrefix = idPrefix;
    }

    /**
     * Returns the cleaned markup of the icon.
     *
     * @param markup   the SVG as written by the module author
     * @param idPrefix put in front of every id and every reference to an id inside the icon
     */
    static String sanitize(String markup, String idPrefix) throws ModuleException {
        if (markup == null || markup.trim().isEmpty()) {
            throw new ModuleException("the icon is empty");
        }
        if (markup.length() > MAX_CHARS) {
            throw new ModuleException("the icon is larger than " + (MAX_CHARS / 1024) + " KB");
        }

        Element root = parse(markup).getDocumentElement();
        if (!isAllowed(root) || !"svg".equals(root.getLocalName())) {
            throw new ModuleException("the icon must start with an <svg> element");
        }

        SvgIcon icon = new SvgIcon(idPrefix);
        icon.write(root, 0);
        if (icon.elements < 2) {
            throw new ModuleException("the icon contains no shapes");
        }
        return icon.out.toString();
    }

    // ---------------------------------------------------------------- parsing

    private static Document parse(String markup) throws ModuleException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Without a DOCTYPE there are no entities and no external files, so the one feature that
            // matters is required. The others are extra safety that not every parser knows.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            optional(factory, "http://xml.org/sax/features/external-general-entities", false);
            optional(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            optional(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // The default handler stays silent for warnings and throws for fatal errors
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("external resources are not allowed");
            });
            return builder.parse(new InputSource(new StringReader(markup)));
        } catch (ParserConfigurationException e) {
            throw new ModuleException("this server cannot read SVG icons safely: " + e.getMessage());
        } catch (SAXException | IOException e) {
            throw new ModuleException("the icon is not valid SVG: " + e.getMessage());
        }
    }

    private static void optional(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException | RuntimeException ignored) {
            // This parser does not know the feature
        }
    }

    // ---------------------------------------------------------------- writing

    private static boolean isAllowed(Element element) {
        String namespace = element.getNamespaceURI();
        boolean svgNamespace = namespace == null || namespace.isEmpty() || SVG_NS.equals(namespace);
        return svgNamespace && element.getLocalName() != null && ELEMENTS.contains(element.getLocalName());
    }

    private void write(Element element, int depth) throws ModuleException {
        if (++elements > MAX_ELEMENTS) {
            throw new ModuleException("the icon has more than " + MAX_ELEMENTS + " elements");
        }
        if (depth > MAX_DEPTH) {
            throw new ModuleException("the icon is nested deeper than " + MAX_DEPTH + " levels");
        }

        boolean root = depth == 0;
        Map<String, String> attributes = attributes(element, root);

        out.append('<').append(element.getLocalName());
        if (root) {
            out.append(" xmlns=\"").append(SVG_NS).append('"');
            out.append(" viewBox=\"").append(viewBox(element)).append('"');
            // Shapes without a color follow the text color of the panel
            if (!attributes.containsKey("fill")) {
                out.append(" fill=\"currentColor\"");
            }
            out.append(" aria-hidden=\"true\" focusable=\"false\"");
        }
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            out.append(' ').append(attribute.getKey()).append("=\"").append(escape(attribute.getValue())).append('"');
        }
        out.append('>');

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            // Text, comments, scripts, styles, images and everything else that is not listed are left out
            if (child.getNodeType() == Node.ELEMENT_NODE && isAllowed((Element) child)) {
                write((Element) child, depth + 1);
            }
        }
        out.append("</").append(element.getLocalName()).append('>');
    }

    private Map<String, String> attributes(Element element, boolean root) {
        Map<String, String> result = new LinkedHashMap<>();
        NamedNodeMap all = element.getAttributes();
        for (int i = 0; i < all.getLength(); i++) {
            Attr attribute = (Attr) all.item(i);
            String local = attribute.getLocalName();
            String namespace = attribute.getNamespaceURI();
            if (local == null) {
                continue;
            }

            String name;
            if (namespace == null || namespace.isEmpty()) {
                name = local;
            } else if (XLINK_NS.equals(namespace) && local.equals("href")) {
                // Gradients refer to each other with xlink:href, browsers also accept plain href
                name = "href";
            } else {
                // Includes the xmlns declarations
                continue;
            }
            if (!ATTRIBUTES.contains(name) || (root && ROOT_IGNORED.contains(name))) {
                continue;
            }

            String value = clean(name, attribute.getValue());
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    private String viewBox(Element root) {
        String value = root.getAttribute("viewBox");
        return VIEW_BOX.matcher(value).matches() ? value.trim() : DEFAULT_VIEW_BOX;
    }

    /** Returns the value as it may be written, or null if the attribute has to be dropped. */
    private String clean(String name, String raw) {
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_VALUE_CHARS) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return null;
            }
        }

        if (name.equals("id")) {
            return ID.matcher(value).matches() ? idPrefix + value : null;
        }
        if (name.equals("href")) {
            // Only a reference to something inside the icon
            return value.startsWith("#") && ID.matcher(value.substring(1)).matches()
                    ? "#" + idPrefix + value.substring(1)
                    : null;
        }

        // The only url() that is allowed points to an id inside the icon
        String rest = REFERENCE.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
        if (rest.contains("url(") || rest.contains("javascript:") || rest.contains("data:")) {
            return null;
        }

        Matcher matcher = REFERENCE.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("url(#" + idPrefix + matcher.group(1) + ")"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    result.append("&amp;");
                    break;
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                default:
                    result.append(c);
            }
        }
        return result.toString();
    }
}
