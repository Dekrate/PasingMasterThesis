# Nazwa pliku: test_deduplikator.py
import unittest
from unittest.mock import patch
import deduplikator # Założenie, że główny skrypt nazywa się deduplikator.py

class TestDeduplicatorScript(unittest.TestCase):

    def setUp(self):
        """Przygotowuje dane testowe (jako słowniki) przed każdym testem."""
        self.header = ['author', 'commit_id', 'feature_name', 'code_snippet']
        self.row1_dict = {'author': 'author1', 'commit_id': 'c1', 'feature_name': 'var', 'code_snippet': 'var x = 10;'}
        self.row2_dict = {'author': 'author2', 'commit_id': 'c2', 'feature_name': 'var', 'code_snippet': 'var y = 20;'}
        self.row3_duplicate_dict = {'author': 'author3', 'commit_id': 'c3', 'feature_name': 'var', 'code_snippet': 'var x = 10;'}
        self.row4_unique_dict = {'author': 'author4', 'commit_id': 'c4', 'feature_name': 'sealed', 'code_snippet': 'sealed class A;'}
        self.row5_duplicate_dict = {'author': 'author5', 'commit_id': 'c5', 'feature_name': 'var', 'code_snippet': 'var x = 10;'}

    def test_normalize_snippet(self):
        """Testuje, czy normalizacja kodu działa poprawnie."""
        snippet = "  var x = 10; [struct:context] [ctx:other] \n "
        expected = "var x = 10;"
        self.assertEqual(deduplikator.normalize_snippet(snippet), expected)

    # Używamy patch na csv.DictWriter oraz read_data_with_header_check
    @patch('csv.DictWriter')
    @patch('deduplikator.read_data_with_header_check')
    def test_no_duplicates(self, mock_read_data, mock_csv_writer):
        """Testuje plik bez żadnych duplikatów."""
        test_data = [self.row1_dict, self.row2_dict, self.row4_unique_dict]
        mock_read_data.return_value = (test_data, self.header)

        # Udajemy, że zapis do pliku się odbył, ale nic nie robimy
        with patch("builtins.open", unittest.mock.mock_open()):
            deduplikator.process_log_file("dummy_path.csv")

        # Sprawdzamy, co zostało przekazane do metody writerows
        # .return_value odnosi się do instancji writera
        # .writerows to metoda, którą chcemy sprawdzić
        # .call_args to argumenty, z którymi została wywołana
        # [0][0] to pierwszy argument pozycyjny pierwszego wywołania
        written_rows = mock_csv_writer.return_value.writerows.call_args[0][0]

        self.assertEqual(len(written_rows), 3)

    @patch('csv.DictWriter')
    @patch('deduplikator.read_data_with_header_check')
    @patch('builtins.input', return_value='T') # Użytkownik zawsze odpowiada 'T'
    def test_all_duplicates_removed(self, mock_input, mock_read_data, mock_csv_writer):
        """Testuje, czy wszystkie duplikaty są usuwane."""
        test_data = [self.row1_dict, self.row2_dict, self.row3_duplicate_dict, self.row5_duplicate_dict]
        mock_read_data.return_value = (test_data, self.header)

        with patch("builtins.open", unittest.mock.mock_open()):
            deduplikator.process_log_file("dummy_path.csv")

        written_rows = mock_csv_writer.return_value.writerows.call_args[0][0]

        self.assertEqual(len(written_rows), 2)
        result_snippets = {row['code_snippet'] for row in written_rows}
        self.assertIn(self.row1_dict['code_snippet'], result_snippets)
        self.assertIn(self.row2_dict['code_snippet'], result_snippets)

    @patch('csv.DictWriter')
    @patch('deduplikator.read_data_with_header_check')
    @patch('builtins.input', side_effect=['N', 'T']) # Odpowiedzi: Nie, Tak
    def test_mixed_decisions(self, mock_input, mock_read_data, mock_csv_writer):
        """Testuje, czy mieszane decyzje (T/N) są poprawnie obsługiwane."""
        test_data = [self.row1_dict, self.row3_duplicate_dict, self.row5_duplicate_dict]
        mock_read_data.return_value = (test_data, self.header)

        with patch("builtins.open", unittest.mock.mock_open()):
            deduplikator.process_log_file("dummy_path.csv")

        written_rows = mock_csv_writer.return_value.writerows.call_args[0][0]

        self.assertEqual(len(written_rows), 2)
        result_commits = {row['commit_id'] for row in written_rows}
        self.assertIn('c1', result_commits) # Z oryginału
        self.assertIn('c3', result_commits) # Z zachowanego duplikatu ('N')
        self.assertNotIn('c5', result_commits) # Z usuniętego duplikatu ('T')

if __name__ == '__main__':
    unittest.main()