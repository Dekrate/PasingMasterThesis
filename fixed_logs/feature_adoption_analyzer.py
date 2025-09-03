import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from datetime import datetime
from scipy import stats

# --- Configuration ---
CSV_FILE_PATH = "analysis_results.csv"

# Map to store feature release dates (Feature Name -> Release Date)
FEATURE_RELEASE_DATES = {
    "Var Keyword Usage": datetime(2018, 3, 20).date(),
    "Switch Expressions": datetime(2020, 3, 17).date(),
    "Record Declarations": datetime(2021, 3, 16).date(),
    "Sealed/Non-Sealed Classes and Interfaces": datetime(2021, 9, 14).date(),
    "Pattern Matching for Switch": datetime(2023, 9, 19).date(),
    "Text Blocks": datetime(2020, 9, 15).date(),
}

def analyze_feature_adoption():
    feature_adoption_times = {}  # Feature Name -> List of adoption times in days

    try:
        # The CSV is malformed: the header has 6 columns, but data rows have 8
        # because decimal numbers (e.g., "5,87") are split into two fields ("5" and "87").
        # We read the data without the header and manually combine the columns.

        col_names = [
            'Repozytorium', 'Nazwa Cechy',
            'Procent_int', 'Procent_dec',
            'Łączna Liczba Wystąpień',
            'Wystapienia_int', 'Wystapienia_dec',
            'Data Pierwszego Użycia'
        ]

        # Read the CSV, skipping the incorrect header row.
        # We specify 8 column names to match the actual data structure.
        # on_bad_lines='skip' will ignore lines with incorrect number of fields (like the original header)
        df = pd.read_csv(CSV_FILE_PATH, header=None, names=col_names, skiprows=1, on_bad_lines='skip')

        # Manually combine the integer and decimal parts of the numeric columns.
        # We convert to string first to avoid errors and then concatenate with a dot.
        df['Procent Plików'] = df['Procent_int'].astype(str) + '.' + df['Procent_dec'].astype(str)
        df['Wystąpienia na 1000 Linii Kodu'] = df['Wystapienia_int'].astype(str) + '.' + df['Wystapienia_dec'].astype(str)

        # Remove the now-redundant intermediate columns.
        df = df.drop(columns=['Procent_int', 'Procent_dec', 'Wystapienia_int', 'Wystapienia_dec'])

        # Ensure the columns are in the logical order expected by the rest of the script.
        df = df[['Repozytorium', 'Nazwa Cechy', 'Procent Plików', 'Łączna Liczba Wystąpień', 'Wystąpienia na 1000 Linii Kodu', 'Data Pierwszego Użycia']]

        # The original file contains repeated headers. We filter these out.
        df = df[df['Repozytorium'] != 'Repozytorium']

        # Now, convert the combined string columns to their proper numeric/date types.
        numeric_cols = ['Procent Plików', 'Łączna Liczba Wystąpień', 'Wystąpienia na 1000 Linii Kodu']
        for col in numeric_cols:
            df[col] = pd.to_numeric(df[col], errors='coerce')

        df['Data Pierwszego Użycia'] = pd.to_datetime(df['Data Pierwszego Użycia'], errors='coerce')

        # Drop any rows where the essential date information could not be parsed.
        df.dropna(subset=['Data Pierwszego Użycia'], inplace=True)

    except FileNotFoundError:
        print(f"Błąd: Nie znaleziono pliku CSV w {CSV_FILE_PATH}")
        return
    except Exception as e:
        print(f"Wystąpił błąd podczas wczytywania lub przetwarzania danych: {e}")
        return

    for index, row in df.iterrows():
        feature_name = str(row["Nazwa Cechy"]).strip()
        first_usage_date = row["Data Pierwszego Użycia"].date()

        if feature_name in FEATURE_RELEASE_DATES:
            release_date = FEATURE_RELEASE_DATES[feature_name]
            days_between = (first_usage_date - release_date).days
            if days_between >= 0:
                feature_adoption_times.setdefault(feature_name, []).append(days_between)

    # --- Analysis per Feature ---
    print("--- Analysis per Feature ---")
    for feature, adoption_times in feature_adoption_times.items():
        if adoption_times:
            adoption_array = np.array(adoption_times)
            mean_adoption = np.mean(adoption_array)
            median_adoption = np.median(adoption_array)
            std_dev_adoption = np.std(adoption_array, ddof=1) # ddof=1 for sample standard deviation

            print(f"\nFeature: {feature}")
            print(f"  Mean Adoption Time: {mean_adoption:.2f} days")
            print(f"  Median Adoption Time: {median_adoption:.2f} days")
            print(f"  Standard Deviation: {std_dev_adoption:.2f} days")

            # Data for plotting adoption over time
            print("  Adoption Dates (for plotting):")
            sorted_dates = sorted([FEATURE_RELEASE_DATES[feature] + pd.Timedelta(days=d) for d in adoption_times])
            for date in sorted_dates:
                print(f"    {date.strftime('%Y-%m-%d')}")
        else:
            print(f"\nFeature: {feature} - No valid adoption data found.")

    # --- Overall Analysis (All Features Combined) ---
    print("\n--- Overall Analysis (All Features Combined) ---")
    all_adoption_times = []
    for times in feature_adoption_times.values():
        all_adoption_times.extend(times)

    if all_adoption_times:
        overall_array = np.array(all_adoption_times)
        overall_mean = np.mean(overall_array)
        overall_median = np.median(overall_array)
        overall_std_dev = np.std(overall_array, ddof=1)

        print(f"  Overall Mean Adoption Time: {overall_mean:.2f} days")
        print(f"  Overall Median Adoption Time: {overall_median:.2f} days")
        print(f"  Overall Standard Deviation: {overall_std_dev:.2f} days")
    else:
        print("No valid overall adoption data found.")

    # --- Test Istotności Różnic w Czasie Adopcji (Pairwise T-Tests) ---
    print("\n--- Test Istotności Różnic w Czasie Adopcji (Pairwise T-Tests) ---")

    features_with_enough_data = {
        f: np.array(times) for f, times in feature_adoption_times.items() if len(times) >= 2
    }

    if len(features_with_enough_data) < 2:
        print("Niewystarczająca liczba cech z danymi do przeprowadzenia testów t.")
    else:
        feature_names = list(features_with_enough_data.keys())
        for i in range(len(feature_names)):
            for j in range(i + 1, len(feature_names)):
                feature1 = feature_names[i]
                feature2 = feature_names[j]

                data1 = features_with_enough_data[feature1]
                data2 = features_with_enough_data[feature2]

                print(f"\nPorównanie: '{feature1}' vs '{feature2}'")

                try:
                    # F-Test for equality of variances (manual calculation as scipy.stats.f_oneway is for means)
                    variance1 = np.var(data1, ddof=1)
                    variance2 = np.var(data2, ddof=1)

                    if variance1 == 0 and variance2 == 0:
                        print("  Obie wariancje są zerowe. Zakładamy równe wariancje.")
                        t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=True)
                        print(f"  Student's T-Test (Equal Variances) P-value: {p_value:.4f}")
                        if p_value < 0.05:
                            print("  Różnica w średnich jest statystycznie istotna (p < 0.05).")
                        else:
                            print("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).")
                        continue
                    elif variance1 == 0 or variance2 == 0:
                        print("  Jedna z wariancji jest zerowa. Nie można wykonać testu F. Użyto testu t Welcha.")
                        t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=False)
                        print(f"  Welch's T-Test (Unequal Variances) P-value: {p_value:.4f}")
                        if p_value < 0.05:
                            print("  Różnica w średnich jest statystycznie istotna (p < 0.05).")
                        else:
                            print("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).")
                        continue

                    # Calculate F-statistic and p-value for variance equality
                    if variance1 >= variance2:
                        f_statistic = variance1 / variance2
                        df1 = len(data1) - 1
                        df2 = len(data2) - 1
                    else:
                        f_statistic = variance2 / variance1
                        df1 = len(data2) - 1
                        df2 = len(data1) - 1

                    if df1 <= 0 or df2 <= 0:
                        print("  Niewystarczająca liczba punktów danych dla testu F (df <= 0). Pomijam test F i używam testu t Welcha.")
                        t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=False)
                        print(f"  Welch's T-Test (Unequal Variances) P-value: {p_value:.4f}")
                        if p_value < 0.05:
                            print("  Różnica w średnich jest statystycznie istotna (p < 0.05).")
                        else:
                            print("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).")
                        continue

                    f_test_p_value = 2 * min(stats.f.cdf(f_statistic, df1, df2), 1 - stats.f.cdf(f_statistic, df1, df2))
                    print(f"  F-Test P-value (Equality of Variances): {f_test_p_value:.4f}")

                    if f_test_p_value < 0.05:
                        t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=False) # Welch's t-test
                        t_test_type = "Welch's T-Test (Unequal Variances)"
                        print("  Wariancje są statystycznie różne. Użyto testu t Welcha.")
                    else:
                        t_stat, p_value = stats.ttest_ind(data1, data2, equal_var=True) # Student's t-test
                        t_test_type = "Student's T-Test (Equal Variances)"
                        print("  Wariancje nie są statystycznie różne. Użyto testu t Studenta.")

                    print(f"  {t_test_type} P-value: {p_value:.4f}")
                    if p_value < 0.05:
                        print("  Różnica w średnich jest statystycznie istotna (p < 0.05).")
                    else:
                        print("  Różnica w średnich nie jest statystycznie istotna (p >= 0.05).")

                except Exception as e:
                    print(f"Error performing statistical test for {feature1} vs {feature2}: {e}")

        print("\nUwaga: Wykonano wiele testów. Dla formalnej analizy rozważ zastosowanie korekcji na wielokrotne porównania (np. Bonferroniego), aby uniknąć błędu typu I.")
        print("Uwaga: Testy t zakładają normalność rozkładu danych. Warto to zweryfikować dla pełnej analizy.")



    print("\n--- Advanced Analysis Notes ---")
    print("To perform 'analiza przeżycia', and 'analiza skupień':")
    print("- More features with adoption data are needed for meaningful comparisons and modeling.")
    print("- Specific statistical tests (e.g., t-test, ANOVA for significance tests) and modeling approaches (e.g., linear regression, survival models like Kaplan-Meier or Cox proportional hazards, clustering algorithms like K-Means) would need to be implemented using Apache Commons Math or other libraries.");
    print("- Detailed requirements for these advanced analyses (e.g., what constitutes a 'trend', what variables to include in models, how to define clusters) are necessary for implementation.")

    # --- Generate Plots for Each Feature ---
    plot_data = []
    for index, row in df.iterrows():
        feature_name = str(row["Nazwa Cechy"]).strip()
        first_usage_date = row["Data Pierwszego Użycia"].date()

        if feature_name in FEATURE_RELEASE_DATES:
            release_date = FEATURE_RELEASE_DATES[feature_name]
            days_to_adoption = (first_usage_date - release_date).days
            if days_to_adoption >= 0:
                plot_data.append({
                    'feature': feature_name,
                    'days_to_adoption': days_to_adoption,
                    'repository': row["Repozytorium"]
                })

    if plot_data:
        # Get unique features from the data
        unique_features = sorted(list(set(d['feature'] for d in plot_data)))

        for feature_name in unique_features:
            # Filter data for the current feature
            feature_specific_data = [d for d in plot_data if d['feature'] == feature_name]
            
            if not feature_specific_data:
                continue

            # Sort by repository name for consistent plotting
            feature_specific_data.sort(key=lambda x: x['repository'])

            repositories = [d['repository'] for d in feature_specific_data]
            days_to_adoption = [d['days_to_adoption'] for d in feature_specific_data]

            plt.figure(figsize=(12, 8))
            
            # Create bar chart
            plt.bar(repositories, days_to_adoption, color='skyblue')

            plt.xlabel("Repozytorium")
            plt.ylabel("Dni od wydania do pierwszego użycia")
            plt.title(f"Czas adopcji dla cechy: {feature_name}")
            plt.xticks(rotation=45, ha="right") # Rotate labels for better fit
            plt.grid(axis='y', linestyle='--', alpha=0.7)
            plt.tight_layout()

            # Sanitize feature name for a valid filename
            safe_feature_name = "".join(c for c in feature_name if c.isalnum() or c in (' ', '_')).rstrip().replace(' ', '_').replace('/', '_')
            
            plot_filename = f"adopcja_{safe_feature_name}.png"
            plt.savefig(plot_filename)
            plt.close()
            print(f"Wygenerowano wykres: {plot_filename}")
    else:
        print("Brak danych do wygenerowania wykresów.")


if __name__ == "__main__":
    analyze_feature_adoption()
