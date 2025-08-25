package com.yourcompany.game;

import java.util.Map;

public class RepositoryMetrics {
	private final int totalJavaFilesProcessed;
	private final long totalLinesOfCode;
	private final Map<String, FeatureMetrics> featureMetrics;

	public RepositoryMetrics(int totalJavaFilesProcessed, long totalLinesOfCode, Map<String, FeatureMetrics> featureMetrics) {
		this.totalJavaFilesProcessed = totalJavaFilesProcessed;
		this.totalLinesOfCode = totalLinesOfCode;
		this.featureMetrics = featureMetrics;
	}

	public int getTotalJavaFilesProcessed() { return totalJavaFilesProcessed; }
	public long getTotalLinesOfCode() { return totalLinesOfCode; }
	public Map<String, FeatureMetrics> getFeatureMetrics() { return featureMetrics; }
}

