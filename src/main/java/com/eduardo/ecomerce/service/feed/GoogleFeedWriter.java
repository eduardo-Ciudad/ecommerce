package com.eduardo.ecomerce.service.feed;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.List;


public final class GoogleFeedWriter {

    static final String G_NS = "http://base.google.com/ns/1.0";

    private GoogleFeedWriter() {
    }

    public static String write(List<GoogleFeedItem> items, String siteUrl) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("rss");
            xml.writeAttribute("version", "2.0");
            xml.writeNamespace("g", G_NS);
            xml.writeStartElement("channel");
            element(xml, "title", "GabiKids");
            element(xml, "link", siteUrl);
            element(xml, "description", "Catálogo de moda infantil GabiKids");

            for (GoogleFeedItem item : items) {
                xml.writeStartElement("item");
                g(xml, "id", item.id());
                g(xml, "item_group_id", item.itemGroupId());
                g(xml, "title", item.title());
                g(xml, "description", item.description());
                g(xml, "link", item.link());
                g(xml, "image_link", item.imageLink());
                for (String extra : item.additionalImageLinks()) {
                    g(xml, "additional_image_link", extra);
                }
                g(xml, "availability", item.availability());
                g(xml, "price", item.price());
                g(xml, "condition", item.condition());
                g(xml, "brand", item.brand());
                g(xml, "gtin", item.gtin());
                g(xml, "identifier_exists", item.identifierExists());
                g(xml, "product_type", item.productType());
                g(xml, "color", item.color());
                g(xml, "size", item.size());
                g(xml, "gender", item.gender());
                g(xml, "age_group", item.ageGroup());
                xml.writeEndElement();
            }

            xml.writeEndElement(); // channel
            xml.writeEndElement(); // rss
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar o feed do Google", e);
        }
        return out.toString();
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static void g(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        if (value == null || value.isBlank()) {
            return;
        }
        xml.writeStartElement("g", name, G_NS);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }
}