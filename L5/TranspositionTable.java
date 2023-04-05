package connectx.L5;

import java.util.Random;

import connectx.CXBoard;

public class TranspositionTable {
    private Random rand;
    private int[][] transposition_table;
    private long[][][] zobrist;
    private long currenthash;
    private int best_move;
    private static int SIZE = 1024 * 1024 * 4; // 4MB
    private static int flag_correct = 0;
    private static int flag_lower = -1;
    private static int flag_upper = 1;
    private static int ERROR = Integer.MAX_VALUE;

    public TranspositionTable(CXBoard B) {
        zobrist = new long[B.M][B.N][2];
        for (int i = 0; i < B.M; i++) {
            for (int j = 0; j < B.N; j++) { // initializing the zobrist table
                zobrist[i][j][0] = rand.nextLong();
                zobrist[i][j][1] = rand.nextLong();
            }
        }
        transposition_table = new int[SIZE][];
        currenthash = 0;
    }

    public int getIndex() {
        return (int) Math.abs((currenthash % SIZE));
    }

    public void put(int depth, int bestMOVE, int value, int flag_type) {
        long mask = 0xFFFFFFFFL;
        int index1 = (int) (currenthash & mask); // non possiamo memorizzare un dato di tipo long nella transposition
                                                 // table, allora salviamo due indici
        // il primo formato dai primi 32 bit del dato e il secondo dai 32 bit restanti.
        int index2 = (int) (currenthash >>> 32);

        transposition_table[getIndex()] = new int[] { index1, index2, depth, bestMOVE, value, flag_type };
    }

    public int get(int depth, int alpha, int beta) {
        int[] element = transposition_table[getIndex()];
        long mask = 0xFFFFFFFFL;
        int index1 = (int) (currenthash & mask);
        int index2 = (int) (currenthash >>> 32); // mi serve per controllare che l'elemento sia quello da me ricercato
        if (transposition_table[getIndex()] != null) {
            if (element != null && index1 == element[0] && index2 == element[1]) {
                if (element[2] >= depth) {
                    if (element[5] == flag_lower && element[4] <= alpha) {
                        return alpha; // it is too low
                    } else if (element[5] == flag_upper && element[4] >= beta) {
                        return beta; // too good to be real
                    } else {
                        return element[4]; // we found the best value
                    }
                }
            }
            best_move = element[3]; // else we save the current bestMove
        }
        return ERROR; // in case we didn't find anything
    }

}

/*
 * Idea dietro lo zobrist system:
 * 1 -> genrare un random long per ogni possibile cella della griglia
 * 2 -> si inizia con un hash = 0 e poi bisogna fare lo XOR con il valore
 * zobrist precedente per quella cella per ottenere l'indice di quella cella
 * nella
 * transposition table
 * FINE ZOBRIST
 * 
 * Transposition Table:
 * 1-> i valori nella trasposition table vanno cercati medinate l'indice hash
 * generato prima
 * 2-> per ogni entry nella transposition table dobbiamo memorizzare -> score,
 * bestmove, zobristkey, flag (ci dice quanto è buono il valore), profondità.
 * Articolo : https://stackoverflow.com/questions/20009796/transposition-tables
 * 
 * PROBLEMA:
 * Come inserire una chiave hash di tipo long in una transposition table di tipo
 * int?
 * SOLUZIONE: If you need to store a long value in a transposition table without
 * increasing the overall size of the table,
 * you can use a technique called key-value splitting. The basic idea is to
 * split the long key into two smaller int keys and store
 * them separately in the table along with the corresponding value.
 * 
 * 
 */