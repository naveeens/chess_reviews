#!/usr/bin/env python3
"""Checks a lesson PGN with Stockfish and writes it back with evaluations and glyphs.

    ~/.venvs/chess/bin/python tools/lessons/annotate.py seeds/italian.pgn out.pgn [--depth 18] [--engine /usr/games/stockfish]

For every position: the evaluation goes into the comment as [%eval 0.25] (white's point of view; the app ignores it),
and a position glyph ($10 =, $14 +=, $16 +/-, ...) is added. Each move of yours also gets ?!, ? or ?? when it gives
away more than the engine's best would have, so a lesson never teaches a blunder by accident. Comments you wrote are kept.
A report of every line and every flagged move is printed, which is what the comments are written from.
"""
import argparse, re, sys
import chess, chess.engine, chess.pgn

POSITION_NAGS = [(25, 10), (100, 14), (300, 16), (10**6, 18)]  # =, +=, +/-, +-  (the black ones are +1)
LOSS_NAGS = [(300, 4), (150, 2), (60, 6)]                       # ??, ?, ?!


def position_nag(cp_white):
    for limit, nag in POSITION_NAGS:
        if abs(cp_white) < limit:
            return nag if nag == 10 else nag + (0 if cp_white > 0 else 1)
    return 18


def loss_nag(loss):
    for limit, nag in LOSS_NAGS:
        if loss >= limit:
            return nag
    return None


class Analyst:
    def __init__(self, path, depth):
        self.engine = chess.engine.SimpleEngine.popen_uci(path)
        self.engine.configure({"Threads": 2, "Hash": 256})
        self.depth, self.cache = depth, {}

    def lines(self, board):
        key = board.fen()
        if key not in self.cache:
            info = self.engine.analyse(board, chess.engine.Limit(depth=self.depth), multipv=3)
            self.cache[key] = [(i["score"].white().score(mate_score=10000), i["pv"][0]) for i in info if "pv" in i]
        return self.cache[key]

    def close(self):
        self.engine.quit()


def eval_comment(cp):
    return "[%%eval %.2f]" % (cp / 100)


def walk(node, board, an, learner, report, indent=0, prev_nag=None):
    """Annotates the children of `node`; `board` is the position at `node`."""
    for child in node.variations:
        move = child.move
        san = board.san(move)
        before = an.lines(board)
        best_cp = before[0][0]
        after_board = board.copy(); after_board.push(move)
        after = an.lines(after_board) if not after_board.is_game_over() else [(0 if not after_board.is_checkmate() else (10000 if board.turn else -10000), None)]
        cp = after[0][0]
        sign = 1 if board.turn == chess.WHITE else -1
        loss = max(0, (best_cp - cp) * sign)
        mover_is_learner = board.turn == learner
        num = ("%d." % board.fullmove_number) if board.turn == chess.WHITE else ("%d..." % board.fullmove_number)
        tag = "  " * indent + f"{num} {san}"
        best_san = board.san(before[0][1]) if before else "?"
        report.append((tag, cp, loss, best_san, mover_is_learner, child is not node.variations[0]))

        nags = {n for n in child.nags if not (10 <= n <= 19) and n not in (2, 4, 6)}  # glyphs from an earlier run are redone
        if mover_is_learner and (g := loss_nag(loss)):
            nags.add(g)
        here = position_nag(cp)
        if here != prev_nag:  # only where the verdict changes, so the board is not covered in signs
            nags.add(here)
        child.nags = nags
        # keep whatever was written, replace only the evaluation
        text = re.sub(r"\[%eval [^\]]*\]\s*", "", child.comment).strip()
        child.comment = (eval_comment(cp) + (" " + text if text else "")).strip()
        walk(child, after_board, an, learner, report, indent + (1 if child is not node.variations[0] else 0), here)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src"); ap.add_argument("dst")
    ap.add_argument("--depth", type=int, default=18)
    ap.add_argument("--engine", default="/usr/games/stockfish")
    ap.add_argument("--threshold", type=int, default=60, help="centipawns a line may give away before it is flagged")
    args = ap.parse_args()
    an = Analyst(args.engine, args.depth)
    flagged, out = 0, []
    try:
        with open(args.src) as f:
            while (game := chess.pgn.read_game(f)) is not None:
                learner = chess.WHITE if game.headers.get("Learn", "white").lower().startswith("w") else chess.BLACK
                report = []
                walk(game, game.board(), an, learner, report)
                title = game.headers.get("Event", "?")
                print(f"\n== {title} (you play {'White' if learner else 'Black'}) ==")
                for tag, cp, loss, best, mine, side in report:
                    mark = ""
                    if mine and loss >= args.threshold:
                        mark = f"   <-- gives away {loss/100:.2f}, engine prefers {best}"; flagged += 1
                    print(f"{tag:<28}{cp/100:+6.2f}{mark}")
                out.append(game)
    finally:
        an.close()
    with open(args.dst, "w") as f:
        exp = chess.pgn.FileExporter(f)
        for g in out:
            g.accept(exp)
    print(f"\n{flagged} of your moves are flagged. Written to {args.dst}")


if __name__ == "__main__":
    main()
