package com.iotiq.partnererp.unit.products;

import com.iotiq.partnererp.modules.products.service.ProductCsv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCsvTest {

    @Test
    void parsesQuotesEscapesLineEndsAndSkipsEmptyRows() {
        String text = "﻿sku,name\r\n"
                + "A-1,\"Lock, \"\"Pro\"\"\"\r\n"
                + "\r\n"
                + ",\n"
                + "A-2,\"two\nlines\"\n"
                + "A-3,last";
        assertThat(ProductCsv.parse(text)).containsExactly(
                List.of("sku", "name"),
                List.of("A-1", "Lock, \"Pro\""),
                List.of("A-2", "two\nlines"),
                List.of("A-3", "last"));
        assertThat(ProductCsv.parse("")).isEmpty();
        assertThat(ProductCsv.parse("a,b\n")).containsExactly(List.of("a", "b"));
    }

    @Test
    void writesQuotedFieldsAndGuardsFormulas() {
        String csv = ProductCsv.write(List.of("sku", "name"), List.of(
                List.of("A-1", "Lock, \"Pro\""),
                List.of("A-2", "=HYPERLINK(\"x\")"),
                List.of("A-3", "-5 pieces"),
                List.of("A-4", "@SUM(1)")));
        assertThat(csv).isEqualTo("sku,name\r\n"
                + "A-1,\"Lock, \"\"Pro\"\"\"\r\n"
                + "A-2,\"'=HYPERLINK(\"\"x\"\")\"\r\n"
                + "A-3,'-5 pieces\r\n"
                + "A-4,'@SUM(1)\r\n");
    }

    @Test
    void exportedValuesReadBackUnchanged() {
        List<String> row = List.of("=1+2", "Lock, \"Pro\"", "plain", "'quoted", "");
        String csv = ProductCsv.write(List.of("a", "b", "c", "d", "e"), List.of(row));
        List<String> back = ProductCsv.parse(csv).get(1).stream().map(ProductCsv::unguard).toList();
        assertThat(back).containsExactlyElementsOf(row);
    }
}
