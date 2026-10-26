package prosemirrorfx.core.editor;

import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.*;
import java.util.*;

import prosemirrorfx.core.pm.Pm;
import static prosemirrorfx.core.schema.EditorSchema.HARD_BREAK;

/**
 * Find and replace over the document's text, ignoring case, within each block (a match never spans
 * two paragraphs). Matches are document ranges; replacing keeps the formatting of the text it replaces.
 */
public final class Find {

    public record Match(int from, int to) {}

    public static List<Match> all(Node doc, String query) {
        var out = new ArrayList<Match>();
        if (query == null || query.isEmpty()) return out;
        var q = query.toLowerCase(Locale.ROOT);
        doc.descendants((n, pos, parent, i) -> {
            if (!n.isTextblock()) return true;
            var text = blockText(n).toLowerCase(Locale.ROOT);
            for (var at = text.indexOf(q); at >= 0; at = text.indexOf(q, at + Math.max(1, q.length())))
                out.add(new Match(pos + 1 + at, pos + 1 + at + q.length()));
            return false;
        });
        return out;
    }

    /** One character per position, as drawn: text as is, a hard break as a newline, any other leaf as a placeholder. */
    static String blockText(Node textblock) {
        var sb = new StringBuilder();
        for (var c : textblock.getContent().getContent())
            sb.append(c.isText() ? c.getText() : c.getType() == HARD_BREAK ? "\n" : "￼");
        return sb.toString();
    }

    /** The match at or after pos (or before it, going back), wrapping round the document. */
    public static Match next(List<Match> matches, int pos, boolean forward) {
        if (matches.isEmpty()) return null;
        if (forward) {
            for (var m : matches) if (m.from() >= pos) return m;
            return matches.getFirst();
        }
        for (var k = matches.size() - 1; k >= 0; k--) if (matches.get(k).to() <= pos) return matches.get(k);
        return matches.getLast();
    }

    public static Transaction selectMatch(PMEditorState state, Match m) {
        return state.getTr().setSelection(Pm.text(state.getDoc(), m.from(), m.to())).scrollIntoView();
    }

    /** Replace one match; the caret lands after the replacement. */
    public static Transaction replace(PMEditorState state, Match m, String with) {
        var tr = state.getTr();
        tr.insertText(with, m.from(), m.to());
        return tr.scrollIntoView();
    }

    /** Replace every match in one step, so one undo brings them all back. */
    public static Transaction replaceAll(PMEditorState state, List<Match> matches, String with) {
        var tr = state.getTr();
        for (var k = matches.size() - 1; k >= 0; k--) tr.insertText(with, matches.get(k).from(), matches.get(k).to());
        return tr.scrollIntoView();
    }

    private Find() {}
}
