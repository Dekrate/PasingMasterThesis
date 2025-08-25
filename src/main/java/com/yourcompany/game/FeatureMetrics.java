package com.yourcompany.game;

public class FeatureMetrics {
	private final String featureName;
	private final int filesCount;
	private final int totalOccurrences;

	public FeatureMetrics(String featureName, int filesCount, int totalOccurrences) {
		this.featureName = featureName;
		this.filesCount = filesCount;
		this.totalOccurrences = totalOccurrences;
	}

	public String getFeatureName() { return featureName; }
	public int getFilesCount() { return filesCount; }
	public int getTotalOccurrences() { return totalOccurrences; }
}
