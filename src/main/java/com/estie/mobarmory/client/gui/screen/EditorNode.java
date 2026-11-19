package com.estie.mobarmory.client.gui.screen;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One row of the editor tree, plus everything the inspector panel needs to edit it.
 * Pure data, no rendering. Nodes are rebuilt from the entry whenever the structure changes
 * (see EditorTree); labels/summaries are suppliers so text edits show up without a rebuild.
 */
public final class EditorNode {
    
    /** Returns null when the value was accepted and applied, otherwise an error message (value NOT applied). */
    public interface Setter {
        String apply(String value);
    }
    
    public enum Kind {
        /** inline text box, applied on every keystroke */
        TEXT,
        /** button that opens the big text editor popup */
        MULTILINE,
        /** button that advances to the next option */
        CYCLE,
        /** a small fixed set of on/off buttons (green = on, red = off) */
        TOGGLES
    }
    
    public static final class Field {
        public final Kind kind;
        public final String label;
        public final Supplier<String> get;
        public Setter set;
        public Function<String, String> warner;   //optional: returns a soft warning, value is still applied
        public String hint = "";                  //greyed text shown while the box is empty
        public Runnable cycle;                    //CYCLE only
        public String modalTitle;                 //MULTILINE only
        public Predicate<String> modalValidator;  //MULTILINE only
        public String modalWarning;               //MULTILINE only
        public Function<String, String> buttonText; //MULTILINE only: text for the button, given the current value
        public List<String> options;              //TOGGLES only
        public IntPredicate isOn;                 //TOGGLES only
        public IntConsumer toggle;                //TOGGLES only
        
        private Field(Kind kind, String label, Supplier<String> get) {
            this.kind = kind;
            this.label = label;
            this.get = get;
        }
        
        public static Field text(String label, Supplier<String> get, Setter set) {
            Field f = new Field(Kind.TEXT, label, get);
            f.set = set;
            return f;
        }
        
        public static Field multiline(String label, String modalTitle, Supplier<String> get, Setter set,
                                      Predicate<String> validator, String warning) {
            Field f = new Field(Kind.MULTILINE, label, get);
            f.set = set;
            f.modalTitle = modalTitle;
            f.modalValidator = validator;
            f.modalWarning = warning;
            return f;
        }
        
        public static Field toggles(String label, List<String> options, IntPredicate isOn, IntConsumer toggle) {
            Field f = new Field(Kind.TOGGLES, label, () -> "");
            f.options = options;
            f.isOn = isOn;
            f.toggle = toggle;
            return f;
        }
        
        public static Field cycle(String label, Supplier<String> get, Runnable next) {
            Field f = new Field(Kind.CYCLE, label, get);
            f.cycle = next;
            return f;
        }
        
        public Field warn(Function<String, String> warner) {
            this.warner = warner;
            return this;
        }
        
        public Field buttonText(Function<String, String> buttonText) {
            this.buttonText = buttonText;
            return this;
        }
        
        public Field hint(String hint) {
            this.hint = hint;
            return this;
        }
    }
    
    /** Inspector button. confirm == null runs immediately, otherwise asks first. */
    public static final class Action {
        private final String label;
        private final String confirm;
        private final Runnable run;
        
        public Action(String label, String confirm, Runnable run) {
            this.label = label;
            this.confirm = confirm;
            this.run = run;
        }
        
        public String label() { return label; }
        public String confirm() { return confirm; }
        public Runnable run() { return run; }
    }
    
    public final EditorNode parent;
    /** Stable identity (usually the data object) so expand state and selection survive rebuilds. */
    public final Object key;
    public final Supplier<String> label;
    public final Supplier<String> summary;
    public final List<EditorNode> children = new ArrayList<>();
    public final List<Field> fields = new ArrayList<>();
    public final List<Action> actions = new ArrayList<>();
    /** Non-null makes this a "+ Add ..." row: clicking it runs this instead of selecting it. */
    public Runnable activate;
    /** Row label colour; the tree colours rows by type. */
    public int color = 0xFFFFFF;
    /** Optional icon drawn in front of the label when the tree has room for icons. */
    public Supplier<ItemStack> icon;
    
    public EditorNode(EditorNode parent, Object key, Supplier<String> label, Supplier<String> summary) {
        this.parent = parent;
        this.key = key;
        this.label = label;
        this.summary = summary;
    }
    
    public boolean isActionRow() {
        return activate != null;
    }
}