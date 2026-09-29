package com.eduardo.ecomerce.service.feed;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFeedWriterTest {

    private static GoogleFeedItem item(String id, String gtin, String identifierExists) {
        return new GoogleFeedItem(id, "grupo-1", "Vestido Floral & Laço <Rosa>", "Descrição", "https://site/p?id=1&variant=" + id,
                "https://img/1.jpg", List.of("https://img/2.jpg", "https://img/3.jpg"), "in_stock", "49.90 BRL", "new",
                null, gtin, identifierExists, "Meninas > Vestidos", null, "6", "female", "kids");
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String g(Element item, String name) {
        var nodes = item.getElementsByTagNameNS(GoogleFeedWriter.G_NS, name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    @Test
    void writesValidRssWithOneItemPerVariantAndEscapedText() throws Exception {
        String xml = GoogleFeedWriter.write(
                List.of(item("v1", "7900257197337", null), item("v2", null, "no")),
                "https://www.gabikidstore.com");

        Document doc = parse(xml);
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("rss");
        assertThat(doc.getDocumentElement().getAttribute("version")).isEqualTo("2.0");

        var items = doc.getElementsByTagName("item");
        assertThat(items.getLength()).isEqualTo(2);

        Element first = (Element) items.item(0);
        assertThat(g(first, "id")).isEqualTo("v1");
        assertThat(g(first, "title")).isEqualTo("Vestido Floral & Laço <Rosa>");
        assertThat(g(first, "link")).isEqualTo("https://site/p?id=1&variant=v1");
        assertThat(g(first, "gtin")).isEqualTo("7900257197337");
        assertThat(g(first, "identifier_exists")).isNull();
        assertThat(first.getElementsByTagNameNS(GoogleFeedWriter.G_NS, "additional_image_link").getLength()).isEqualTo(2);

        Element second = (Element) items.item(1);
        assertThat(g(second, "gtin")).isNull();
        assertThat(g(second, "identifier_exists")).isEqualTo("no");
        assertThat(g(second, "brand")).isNull(); // campo null é omitido
        assertThat(g(second, "color")).isNull();
    }
}