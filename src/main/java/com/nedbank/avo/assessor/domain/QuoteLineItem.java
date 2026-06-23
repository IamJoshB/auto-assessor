package com.nedbank.avo.assessor.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class QuoteLineItem {

	private String panelKey;
	private String panelLabel;
	private String productName;
	private String productDescription;
	private String productUrl;
	private String productImageUrl;
	private String storedProductImageUrl;
	private String sourceWebsite;
	private double panelPriceZar;
	private double hardwarePriceZar;
	private int quantity;
	private double lineTotalZar;
	private List<String> includedHardware = new ArrayList<>();
	private String notes;

	public QuoteLineItem(
		String panelKey,
		String panelLabel,
		String productName,
		String productDescription,
		String productUrl,
		String productImageUrl,
		String storedProductImageUrl,
		String sourceWebsite,
		double panelPriceZar,
		double hardwarePriceZar,
		int quantity,
		double lineTotalZar,
		List<String> includedHardware,
		String notes
	) {
		this.panelKey = panelKey;
		this.panelLabel = panelLabel;
		this.productName = productName;
		this.productDescription = productDescription;
		this.productUrl = productUrl;
		this.productImageUrl = productImageUrl;
		this.storedProductImageUrl = storedProductImageUrl;
		this.sourceWebsite = sourceWebsite;
		this.panelPriceZar = panelPriceZar;
		this.hardwarePriceZar = hardwarePriceZar;
		this.quantity = quantity;
		this.lineTotalZar = lineTotalZar;
		this.includedHardware = includedHardware == null ? new ArrayList<>() : includedHardware;
		this.notes = notes;
	}
}
