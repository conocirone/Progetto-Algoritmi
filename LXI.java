package connectx.LXI;

import connectx.CXPlayer;
import connectx.CXBoard;
import connectx.CXGameState;
import connectx.CXCell;
import connectx.CXCellState;

import java.util.TreeSet;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import connectx.CXCell;

public class LXI implements CXPlayer {
    private Random rand;
    private CXGameState myWin;
    private CXGameState yourWin;
    private int TIMEOUT;
    private long START;
    private long[][][] zobrist;
    private long hash_value;
    private int size = 2 ^ 24;
    private Entry[] transpositionTable = new Entry[size]; // transposition table structure
    private int hashfEXACT = 0; // hash flag exact
    private int hashfALPHA = 1; // hash flag lower
    private int hashfBETA = 2; // hash flag upper
    private int valUNKOWN = Integer.MIN_VALUE - 1;
    private int timeout_v = 10000;
    private boolean firstPlayer;
    private int[] HistoryTable;

    private static class Entry {
        long key;
        int depth;
        int score;
        int flag;
    } // structure of a transposition table entry

    public LXI() {
    }

    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game
        rand = new Random(System.currentTimeMillis());
        myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
        yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;
        TIMEOUT = timeout_in_secs;
        firstPlayer = first ? true : false;

        zobrist = new long[M][N][2];
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                for (int k = 0; k < 2; k++) {
                    zobrist[i][j][k] = rand.nextLong();
                }
            }
        }
        hash_value = rand.nextLong();
        HistoryTable = new int[N];
    }

    private CXGameState doMove(CXBoard B, int y) {
        int x = getX(B, y);
        hash_value ^= zobrist[x][y][B.currentPlayer()];
        return B.markColumn(y);
    }

    private void undoMove(CXBoard B) {
        CXCell cell = B.getMarkedCells()[B.getMarkedCells().length - 1];
        hash_value ^= zobrist[cell.i][cell.j][B.currentPlayer() == 1 ? 0 : 1]; // la mossa è del player precedente
        B.unmarkColumn();
    }

    private int Index() {
        return Math.abs((int) (hash_value % size));
    }

    private int get(int searchdepth, int alpha, int beta) {
        Entry t_element = transpositionTable[Index()];
        if (t_element != null && t_element.key == hash_value) {
            if (t_element.depth >= searchdepth) {
                if (t_element.flag == hashfEXACT) {
                    return t_element.score;
                } else if (t_element.flag == hashfALPHA && t_element.score <= alpha) {
                    return alpha;
                } else if (t_element.flag == hashfBETA && t_element.score >= beta) {
                    return beta;
                }
            }
        }
        return valUNKOWN;
    }

    private void put(int depth, int val, int hash_flag) {
        transpositionTable[Index()] = new Entry();
        transpositionTable[Index()].key = hash_value;
        transpositionTable[Index()].depth = depth;
        transpositionTable[Index()].flag = hash_flag;
        transpositionTable[Index()].score = val;
    }

    public static int getX(CXBoard B, int y) {
        int count = 0;
        for (int i = 1; i < B.M; i++) {
            if (B.cellState(i, y) != CXCellState.FREE) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
    // usare un array per salvare l'altezza raggiunta in ogni colonna

    public int eval(CXBoard board, int col) {
        int best = 0;
        int secondbest = 0;
        int last_row = getX(board, col);
        CXCellState s = board.cellState(last_row, col);
        int n = 1;

        for (int k = 1; col - k >= 0 && (board.cellState(last_row, col - k) == s
                || board.cellState(last_row, col - k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row, col - k) == s)
                n++;
        } // backward check
        for (int k = 1; col + k < board.N && (board.cellState(last_row, col + k) == s
                || board.cellState(last_row, col + k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row, col + k) == s)
                n++;
        } // forward check
        best = n;

        // Vertical check
        n = 1;
        for (int k = 1; last_row + k < board.M && (board.cellState(last_row + k, col) == s
                || board.cellState(last_row + k, col) == CXCellState.FREE); k++) {
            if (board.cellState(last_row + k, col) == s)
                n++;
        }
        best = Math.max(best, n);
        secondbest = Math.min(best, n);

        // Diagonal check
        n = 1;
        for (int k = 1; last_row - k >= 0 && col - k >= 0
                && (board.cellState(last_row - k, col - k) == s
                        || board.cellState(last_row - k, col - k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row - k, col - k) == s)
                n++;
        } // backward check
        for (int k = 1; last_row + k < board.M && col + k < board.N
                && (board.cellState(last_row + k, col + k) == s
                        || board.cellState(last_row + k, col + k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row + k, col + k) == s)
                n++;
        } // forward check
        if (n > secondbest) {
            best = Math.max(best, n);
            secondbest = Math.min(best, n);
        }

        // Anti-diagonal check
        n = 1;
        for (int k = 1; last_row - k >= 0 && col + k < board.N
                && (board.cellState(last_row - k, col + k) == s || board.cellState(last_row - k, col
                        + k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row - k, col + k) == s)
                n++;
        } // backward check
        for (int k = 1; last_row + k < board.M && col - k >= 0
                && (board.cellState(last_row + k, col - k) == s || board.cellState(last_row + k, col
                        - k) == CXCellState.FREE); k++) {
            if (board.cellState(last_row + k, col - k) == s)
                n++;
        } // forward check
        if (n > secondbest) {
            best = Math.max(best, n);
            secondbest = Math.min(best, n);
        }
        if (best >= board.X)
            return best + secondbest; // in alternativa, Integer.MAX_VALUE
        else {
            int sol = (int) (best + secondbest * 0.5);
            return sol;
        }
    }

    private int[] HistoryHeuristic(CXBoard B) {
        int width = B.getAvailableColumns().length;
        int[] rating = new int[width];
        Integer[] moves = B.getAvailableColumns();
        for (int m = 0; m < width; m++) {
            rating[m] = HistoryTable[moves[m]];
        }

        // Ordina l'array rating in ordine decrescente
        Integer[] indexes = new Integer[width];
        for (int i = 0; i < width; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, Comparator.comparingInt((Integer i) -> rating[i]).reversed());

        // Restituisci i movimenti ordinati in base al rating
        int[] orderedMoves = new int[width];
        for (int i = 0; i < width; i++) {
            orderedMoves[i] = moves[indexes[i]];
        }
        return orderedMoves;
    }

    public int alphabeta(CXBoard board, int depth, int alpha, int beta, boolean maximizingPlayer) {
        int value, valueKind = hashfEXACT;
        int color = 1;
        if (firstPlayer == false) {
            color = -1;
        }

        value = get(depth, alpha, beta);
        if (value != valUNKOWN) {
            return value;
        }
        if (checktime()) {
            return timeout_v;
        }

        if (depth == 0 || board.gameState() != CXGameState.OPEN) {
            if (board.gameState() != CXGameState.OPEN && board.gameState() == myWin) {
                return Integer.MAX_VALUE; // win
            } else if (board.gameState() != CXGameState.OPEN && board.gameState() == yourWin) {
                return Integer.MIN_VALUE; // loss
            } else {
                value = eval(board, board.getLastMove().j) * color;
                put(depth, value, hashfEXACT);
                return value;// draw
            }
        }
        // Massimizzare la valutazione per il giocatore corrente
        if (maximizingPlayer) {
            int bestValue = Integer.MIN_VALUE;
            for (int column : HistoryHeuristic(board)) {
                // Effettuare la mossa sulla colonna selezionata
                doMove(board, column);

                // Calcolare il valore della mossa
                value = alphabeta(board, depth - 1, alpha, beta, false);
                HistoryTable[column] += value * value;
                if (checktime()) {
                    return timeout_v;
                }
                bestValue = Math.max(bestValue, value);
                if (bestValue >= beta) {
                    valueKind = hashfBETA;
                } else if (bestValue <= alpha) {
                    valueKind = hashfALPHA;
                }
                // Aggiornare il valore di alpha
                alpha = Math.max(alpha, bestValue);

                // Annullare la mossa effettuata sulla colonna selezionata
                undoMove(board);

                // Verificare se si può tagliare il ramo
                if (beta <= alpha) {
                    break;
                }
            }
            put(depth, bestValue, valueKind);
            return bestValue;
        }
        // Minimizzare la valutazione per l'avversario
        else {
            int bestValue = Integer.MAX_VALUE;
            for (int column : HistoryHeuristic(board)) {
                // Effettuare la mossa sulla colonna selezionata dall'avversario
                doMove(board, column);

                // Calcolare il valore della mossa
                value = alphabeta(board, depth - 1, alpha, beta, true);
                HistoryTable[column] += value * value;
                if (checktime()) {
                    return timeout_v;
                }
                if (value < bestValue) {
                    bestValue = value;
                }
                if (bestValue <= alpha) {
                    valueKind = hashfALPHA;
                } else if (bestValue >= beta) {
                    valueKind = hashfBETA;
                }

                // Aggiornare il valore di beta
                beta = Math.min(beta, bestValue);

                // Annullare la mossa effettuata sulla colonna selezionata
                undoMove(board);

                // Verificare se si può tagliare il ramo
                if (beta <= alpha) {
                    break;
                }
            }
            put(depth, bestValue, valueKind);
            return bestValue;
        }
    }

    private int alphabetacol(CXBoard B, int depth) {
        int value;
        int bestValue = Integer.MIN_VALUE;
        int bestMove = -1;
        for (int i : HistoryHeuristic(B)) {
            doMove(B, i);
            if (B.gameState() == myWin) {
                System.out.println("Serve a qualcosaID"); // serve a qualcosa
                return i;
            }
            value = alphabeta(B, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
            undoMove(B);
            if (value == timeout_v) {
                break;
            }
            if (value > bestValue) {
                bestValue = value;
                bestMove = i;
            }
        }
        return bestMove;
    }

    public int center(CXBoard B) {
        if (B.getMarkedCells().length == 0 || B.getMarkedCells().length == 1) {
            doMove(B, B.N / 2);
            return B.N / 2;
        }
        return -1;
    }

    public int LosingColumn(CXBoard B) {
        int col = B.getAvailableColumns()[0];
        doMove(B, col);
        for (int k = 1; k < B.getAvailableColumns().length; k++) {
            if (k != col && B.gameState() == CXGameState.OPEN) { // per evitare l'errore 'Game'
                int col_2 = B.getAvailableColumns()[k];
                doMove(B, col_2);
                if (B.gameState() == yourWin) {
                    System.out.println("Sconfitta evitataID");
                    undoMove(B);// UNDO YOUR MOVE
                    undoMove(B);// undo my move
                    doMove(B, col_2); // steal your move
                    return col_2;
                } else {
                    undoMove(B); //
                }
            }
        }
        undoMove(B);
        return -1;
    }

    private int iterativeDeepening(int max_depth, CXBoard B) {
        int current_depth_limit = 3;
        Integer[] L = B.getAvailableColumns();
        int safe = L[rand.nextInt(L.length)]; // Save a random column
        int bestMove = -1;
        while (!checktime() && current_depth_limit <= max_depth && B.gameState() == CXGameState.OPEN) {
            int it_col = alphabetacol(B, current_depth_limit);
            if (it_col == -1) {
                break;
            }
            bestMove = it_col;
            current_depth_limit++;
        }
        if (bestMove == -1) {
            System.out.println("Failed");
            return safe;
        }
        System.out.println("Good Job");
        return bestMove;
    }

    public int selectColumn(CXBoard B) {
        START = System.currentTimeMillis(); // Save starting time
        int col = center(B);
        if (col != -1)
            return col;

        int possible_lose = LosingColumn(B);
        if (possible_lose != -1) {
            return possible_lose;
        }
        int max_depth = (B.M * B.N) - B.getMarkedCells().length;
        int bestMove = iterativeDeepening(max_depth, B);
        return bestMove;
    }

    private boolean checktime() {
        if ((System.currentTimeMillis() - START) / 1000.0 >= TIMEOUT * (99.0 / 100.0)) {
            return true;
        }
        return false;
    }

    public String playerName() {
        return "LXImo";
    }

}
