package selector;

import java.awt.Point;
import java.util.ListIterator;

/**
 * Models a selection tool that connects each added point with a straight line.
 */
public class PointToPointSelectionModel extends SelectionModel {

    public PointToPointSelectionModel(boolean notifyOnEdt) {
        super(notifyOnEdt);
    }

    public PointToPointSelectionModel(SelectionModel copy) {
        super(copy);
    }

    /**
     * Return a straight line segment from our last point to `p`.
     */
    @Override
    public PolyLine liveWire(Point p) {
        return new PolyLine(lastPoint(), p);
    }

    /**
     * Append a straight line segment to the current selection path connecting its end with `p`.
     */
    @Override
    protected void appendToSelection(Point p) {
        selection.addLast(liveWire(p));
    }

    /**
     * Move the starting point of the segment of our selection with index `index` to `newPos`,
     * connecting to the end of that segment with a straight line and also connecting `newPos` to
     * the start of the previous segment (wrapping around) with a straight line (these straight
     * lines replace both previous segments).  Notify listeners that the "selection" property has
     * changed.
     */
    @Override
    public void movePoint(int index, Point newPos) {
        // Confirm that we have a closed selection and that `index` is valid
        if (state() != SelectionState.SELECTED) {
            throw new IllegalStateException("May not move point in state " + state());
        }
        if (index < 0 || index >= selection.size()) {
            throw new IllegalArgumentException("Invalid segment index " + index);
        }

        Point newLoc = new Point(newPos.x, newPos.y); // making a copy to avoid rep exposure
        ListIterator<PolyLine> li = selection.listIterator(index);
        // list iterator with cursor currently just before index

        PolyLine next = li.next(); // get the next segment
        PolyLine nextReplacement = new PolyLine(newLoc, next.end());
        li.set(nextReplacement); // replace next segment with nextReplacement

        if (index == 0) { // index corresponds to starting point of selection
            start = newLoc;

            PolyLine prev = selection.removeLast();
            PolyLine prevReplacement = new PolyLine(prev.start(), newLoc);
            selection.addLast(prevReplacement);

        } else { // index corresponds to middle point of selection
            li.previous(); // go back a step (because we had moved forward previously)
            PolyLine prev = li.previous();// get the previous segment

            PolyLine prevReplacement = new PolyLine(prev.start(), newLoc);
            li.set(prevReplacement); // replace previous segment with prevReplacement
        }

        // Notify observers that the selection has changed.
        propSupport.firePropertyChange("selection", null, selection());


    }
}
