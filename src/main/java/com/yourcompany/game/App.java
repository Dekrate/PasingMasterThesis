
package com.yourcompany.game;

import com.github.javaparser.ParserConfiguration.LanguageLevel;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {

	public static void main(String[] args) {

		List<File> repositoriesToAnalyze = new ArrayList<>();
		repositoriesToAnalyze.add(new File("C:\\Users\\Acer\\Documents\\studia\\praca magisterska\\junit-framework"));




		List<SyntaxAnalyzerStrategy> analysisStrategies = Arrays.asList(
				new SwitchExpressionAnalyzer(),
				new TextBlockAnalyzer(),



				 new SealedClassAnalyzer(),
				 new PatternMatchingSwitchAnalyzer()
		);


		RepositoryAnalyzer analyzer = new RepositoryAnalyzer(analysisStrategies, LanguageLevel.JAVA_21);

		for (File repo : repositoriesToAnalyze) {
			analyzer.analyzeRepository(repo);
			System.out.println("\n-------------------------------------------------\n");
		}

		System.out.println("Analiza wszystkich repozytoriów zakończona.");
	}
}