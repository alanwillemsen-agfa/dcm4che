/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2012
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.dcm4che.util.StringUtils;
import org.dcm4che.util.TagUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class IOD extends ArrayList<IOD.DataElement> {

    private static final long serialVersionUID = -5065822488885801576L;

    public enum DataElementType {
        TYPE_0, TYPE_1, TYPE_2, TYPE_3
    }

    public static class DataElement implements Serializable {

        private static final long serialVersionUID = -7460474415381086525L;

        public final int tag;
        public final VR vr;
        public final DataElementType type;
        public final int minVM;
        public final int maxVM;
        public final int valueNumber;
        private Condition condition;
        private Object values;

        public DataElement(int tag, VR vr, DataElementType type,
                int minVM, int maxVM, int valueNumber) {
            this.tag = tag;
            this.vr = vr;
            this.type = type;
            this.minVM = minVM;
            this.maxVM = maxVM;
            this.valueNumber = valueNumber;
        }

        public DataElement setCondition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Condition getCondition() {
            return condition;
        }

        public int getValueNumber() {
            return valueNumber;
        }

        public DataElement setValues(String... values) {
            this.values = values;
            return this;
        }

        public DataElement setValues(int... values) {
            this.values = values;
            return this;
        }

        public DataElement setItemIOD(IOD iod) {
            this.values = iod;
            return this;
        }

        public Object getValues() {
            return values;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<DataElement keyword=\"")
              .append(ElementDictionary.getStandardElementDictionary().keywordOf(tag))
              .append("\" tag=\"").append(TagUtils.toHexString(tag))
              .append("\" vr=\"").append(vr)
              .append("\" type=\"").append(type);
            if (condition != null)
                sb.append('C');
            sb.append(vr == VR.SQ ? "\" items=\"" : "\" vm=\"");
            if (minVM == -1)
                sb.append('n');
            else
                sb.append(minVM);
            if (maxVM != minVM) {
                sb.append('-');
                if (maxVM == -1)
                    sb.append('n');
                else
                    sb.append(maxVM);
            }
            if (valueNumber != 0)
                sb.append("\" valueNumber=\"").append(valueNumber);
            if (values instanceof String[] || values instanceof int[]) {
                sb.append("\">").append(StringUtils.LINE_SEPARATOR);
                if (values instanceof String[])
                    for (String val : (String[]) values)
                        sb.append("  <Value>").append(val).append("/Value")
                          .append(StringUtils.LINE_SEPARATOR);
                else 
                    for (int val : (int[]) values)
                        sb.append("  <Value>").append(val).append("/Value")
                          .append(StringUtils.LINE_SEPARATOR);
                sb.append("</DataElement>");
            } else {
                sb.append("\"/>");
            }
            return sb.toString();
        }
   }


    public abstract static class Condition {
         protected String id;
         protected boolean not;

         public Condition id(String id) {
             this.id = id;
             return this;
         }

         public final String id() {
             return id;
         }

         public final Condition not() {
             this.not = !not;
             return this;
         }

         public abstract boolean match(Attributes attrs);

         public void addChild(Condition child) {
             throw new UnsupportedOperationException();
         }

         public Condition trim() {
             return this;
         }

         public boolean isEmpty() {
             return false;
         }
    }

    abstract static class CompositeCondition extends Condition  {
        protected final ArrayList<Condition> childs = new ArrayList<Condition>();

        public abstract boolean match(Attributes attrs);

        @Override
        public void addChild(Condition child) {
            childs.add(child);
        }

        @Override
        public Condition trim() {
            int size = childs.size();
            if (size == 1) {
                Condition child = childs.get(0).id(id);
                return not ? child.not() : child;
            }
            childs.trimToSize();
            return this;
        }

        @Override
        public boolean isEmpty() {
            return childs.isEmpty();
        }
    }

    public static class And extends CompositeCondition {

        public boolean match(Attributes attrs) {
            for (Condition child : childs) {
                if (!child.match(attrs))
                    return not;
            }
            return !not;
        }
   }

    public static class Or extends CompositeCondition {

        public boolean match(Attributes attrs) {
            for (Condition child : childs) {
                if (child.match(attrs))
                    return !not;
            }
            return not;
        }
   }

    public static class Present extends Condition {
        private final int tag;

        public Present(int tag) {
            this.tag = tag;
        }

        public boolean match(Attributes attrs) {
            return not ? !attrs.containsValue(tag) : attrs.containsValue(tag);
        }
    }

    public static class MemberOf extends Condition {
        private final int tag;
        private final VR vr;
        private final int valueIndex;
        private final boolean matchNotPresent;
        private Object values;

        public MemberOf(int tag, VR vr, int valueIndex, boolean matchNotPresent) {
            this.tag = tag;
            this.vr = vr;
            this.valueIndex = valueIndex;
            this.matchNotPresent = matchNotPresent;
        }

        public VR vr() {
            return vr;
        }

        public MemberOf setValues(String[] values) {
            this.values = values;
            return this;
        }

        public MemberOf setValues(int[] values) {
            this.values = values;
            return this;
        }

        public boolean match(Attributes attrs) {
            if (values == null)
                throw new IllegalStateException("values not initialized");
            if (values instanceof int[])
                return not ? !match(attrs, ((int[]) values))
                           : match(attrs, ((int[]) values));
            else
                return not ? !match(attrs, ((String[]) values))
                           : match(attrs, ((String[]) values));
        }

        private boolean match(Attributes attrs, String[] ss) {
            String val = attrs.getString(tag, valueIndex);
            if (val == null)
                return not ? !matchNotPresent : matchNotPresent;
            for (String s : ss) {
                if (s.equals(val))
                    return !not;
            }
            return not;
        }

        private boolean match(Attributes attrs, int[] is) {
            int val = attrs.getInt(tag, valueIndex, Integer.MIN_VALUE);
            if (val == Integer.MIN_VALUE)
                return matchNotPresent;
            for (int i : is) {
                if (i == val)
                    return true;
            }
            return false;
        }

    }

    @Override
    public void trimToSize() {
        super.trimToSize();
        for (DataElement el : this) {
            if (el.values instanceof IOD)
                ((IOD) el.values).trimToSize();
        }
    }

    public void parse(String uri) throws IOException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            SAXParser parser = f.newSAXParser();
            parser.parse(uri, new SAXHandler(this));
        } catch (SAXException e) {
            throw new IOException("Failed to parse " + uri, e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SAXHandler extends DefaultHandler {

        private DataElement el;
        private StringBuilder sb = new StringBuilder();
        private boolean processCharacters;
        private String idref;
        private List<String> values = new ArrayList<String>();
        private LinkedList<IOD> iodStack = new LinkedList<IOD>();
        private LinkedList<Condition> conditionStack = new LinkedList<Condition>();
        private Map<String, IOD> id2iod = new HashMap<String, IOD>();
        private Map<String, Condition> id2cond = new HashMap<String, Condition>();

        public SAXHandler(IOD iod) {
            iodStack.add(iod);
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                org.xml.sax.Attributes atts) throws SAXException {
            switch (qName.charAt(0)) {
            case 'A':
                if (qName.equals("And"))
                    startCondition(qName, new And());
                break;
            case 'D':
                if (qName.equals("DataElement"))
                    startDataElement(
                            atts.getValue("tag"),
                            atts.getValue("vr"),
                            atts.getValue("type"),
                            atts.getValue("vm"),
                            atts.getValue("items"),
                            atts.getValue("valueNumber"));
                break;
            case 'I':
                if (qName.equals("If"))
                    startIf(atts.getValue("id"), atts.getValue("idref"));
                else if (qName.equals("Item"))
                    startItem(atts.getValue("id"), atts.getValue("idref"));
                break;
            case 'M':
                if (qName.equals("MemberOf"))
                    startCondition(qName, new MemberOf(
                            tagOf(atts.getValue("tag")),
                            vrOf(atts.getValue("vr")),
                            valueNumberOf(atts.getValue("valueNumber"), 1) - 1,
                            matchNotPresentOf(atts.getValue("matchNotPresent"))));
                break;
            case 'N':
                if (qName.equals("NotAnd"))
                    startCondition(qName, new And().not());
                else if (qName.equals("NotMemberOf"))
                    startCondition(qName, new MemberOf(
                            tagOf(atts.getValue("tag")),
                            vrOf(atts.getValue("vr")),
                            valueNumberOf(atts.getValue("valueNumber"), 1) - 1,
                            matchNotPresentOf(atts.getValue("matchNotPresent")))
                        .not());
                else if (qName.equals("NotOr"))
                    startCondition(qName, new Or().not());
                else if (qName.equals("NotPresent"))
                    startCondition(qName,
                            new Present(tagOf(atts.getValue("tag"))).not());
                break;
            case 'O':
                if (qName.equals("Or"))
                    startCondition(qName, new Or());
                break;
            case 'P':
                if (qName.equals("Present"))
                    startCondition(qName,
                            new Present(tagOf(atts.getValue("tag"))));
                break;
            case 'V':
                if (qName.equals("Value"))
                    startValue();
                break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            switch (qName.charAt(0)) {
            case 'A':
                if (qName.equals("And"))
                    endCondition(qName);
                break;
            case 'D':
                if (qName.equals("DataElement"))
                    endDataElement();
                break;
            case 'I':
                if (qName.equals("If"))
                    endCondition(qName);
                else if (qName.equals("Item"))
                    endItem();
                break;
            case 'M':
                if (qName.equals("MemberOf"))
                    endCondition(qName);
                break;
            case 'N':
                if (qName.equals("NotAnd"))
                    endCondition(qName);
                else if (qName.equals("NotMemberOf"))
                    endCondition(qName);
                else if (qName.equals("NotOr"))
                    endCondition(qName);
                else if (qName.equals("NotPresent"))
                    endCondition(qName);
                break;
            case 'O':
                if (qName.equals("Or"))
                    endCondition(qName);
                break;
            case 'P':
                if (qName.equals("Present"))
                    endCondition(qName);
                break;
            case 'V':
                if (qName.equals("Value"))
                    endValue();
                break;
            }
            processCharacters = false;
            idref = null;
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (processCharacters)
                sb.append(ch, start, length);
        }

        private void startDataElement(String tagStr, String vrStr,
                String typeStr, String vmStr, String items,
                String valueNumberStr) throws SAXException {
            if (idref != null)
                throw new SAXException("<Item> with idref must be empty");

            IOD iod = iodStack.getLast();
            int tag = tagOf(tagStr);
            VR vr = vrOf(vrStr);
            DataElementType type = typeOf(typeStr);
            
            int minVM = -1;
            int maxVM = -1;
            String vm = vr == VR.SQ ? items : vmStr;
            if (vm != null) {
                try {
                    String[] ss = StringUtils.split(vm, '-');
                    if (ss[0].charAt(0) != 'n') {
                        minVM = Integer.parseInt(ss[0]);
                        if (ss.length > 1) {
                            if (ss[1].charAt(0) != 'n')
                                maxVM = Integer.parseInt(ss[1]);
                        } else {
                            maxVM = minVM;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new SAXException(
                            (vr == VR.SQ ? "invalid items=\"" 
                                         : "invalid vm=\"")
                            + vm + '"');
                }
            }
            el = new DataElement(tag, vr, type, minVM, maxVM,
                    valueNumberOf(valueNumberStr, 0));
            iod.add(el);
        }

        private DataElementType typeOf(String s) throws SAXException {
            try {
                return DataElementType.valueOf("TYPE_" + s);
            } catch (NullPointerException e) {
                throw new SAXException("missing type attribute");
            } catch (IllegalArgumentException e) {
                throw new SAXException("unrecognized type=\"" + s + '"');
            }
        }

        private VR vrOf(String s) throws SAXException {
            try {
                return VR.valueOf(s);
            } catch (NullPointerException e) {
                throw new SAXException("missing vr attribute");
            } catch (IllegalArgumentException e) {
                throw new SAXException("unrecognized vr=\"" + s + '"');
            }
        }

        private int tagOf(String s) throws SAXException {
            try {
               return (int) Long.parseLong(s, 16);
            } catch (NullPointerException e) {
                throw new SAXException("missing tag attribute");
            } catch (IllegalArgumentException e) {
                throw new SAXException("invalid tag=\"" + s + '"');
            }
        }

        private int valueNumberOf(String s, int def) throws SAXException {
            try {
               return s != null ? Integer.parseInt(s) : def;
            } catch (IllegalArgumentException e) {
                throw new SAXException("invalid valueNumber=\"" + s + '"');
            }
        }

        private boolean matchNotPresentOf(String s) {
            return s != null && s.equalsIgnoreCase("true");
        }


        private void endDataElement() {
            if (values.isEmpty())
                return;

            if (el.vr.isIntType())
                el.setValues(parseInts(values));
            else
                el.setValues(values.toArray(new String[values.size()]));
            values.clear();
        }

        private int[] parseInts(List<String> list) {
            int[] is = new int[list.size()];
            for (int i = 0; i < is.length; i++)
                is[i] = Integer.parseInt(list.get(i));
            return is;
        }

        private void startValue() {
            sb.setLength(0);
            processCharacters = true;
        }

        private void endValue() {
            values.add(sb.toString());
        }

        private void startItem(String id, String idref) throws SAXException {
            IOD iod;
            if (idref != null) {
                iod = id2iod.get(idref);
                if (iod == null)
                    throw new SAXException(
                            "could not resolve <Item idref:\"" + idref + "\"/>");
            } else { 
                iod = new IOD();
            }
            el.setItemIOD(iod);
            iodStack.add(iod);
            if (id != null)
                id2iod.put(id, iod);

            this.idref = idref;
        }

        private void endItem() {
            iodStack.removeLast();
        }

        private void startIf(String id, String idref) throws SAXException {
            if (el == null || !conditionStack.isEmpty())
                throw new SAXException("unexpected <If>");

            Condition cond;
            if (idref != null) {
                cond = id2cond.get(idref);
                if (cond == null)
                    throw new SAXException(
                            "could not resolve <If idref:\"" + idref + "\"/>");
            } else { 
                cond = new And().id(id);
            }
            conditionStack.add(cond);
            if (id != null)
                id2cond.put(id, cond);
            this.idref = idref;
        }

       private void startCondition(String name, Condition cond)
               throws SAXException {
            try {
                conditionStack.add(cond);
            } catch (Exception e) {
                throw new SAXException("unexpected <" + name + '>');
            }
        }

        private void endCondition(String name) throws SAXException {
            Condition cond = conditionStack.removeLast();
            if (cond.isEmpty())
                throw new SAXException('<' + name + "> must not be empty");

            if (!values.isEmpty()) {
                if (!(cond instanceof MemberOf))
                    throw new SAXException('<' + name + "> cannot contains <Value>");
                
                MemberOf memberOf = (MemberOf) cond;
                if (memberOf.vr.isIntType())
                    memberOf.setValues(parseInts(values));
                else
                    memberOf.setValues(values.toArray(new String[values.size()]));
                values.clear();
            }

            if (conditionStack.isEmpty())
                el.setCondition(cond.trim());
            else
                conditionStack.getLast().addChild(cond.trim());
        }
    }

    public static IOD load(String uri) throws IOException {
        if (uri.startsWith("resource:")) {
            try {
                uri = Thread.currentThread().getContextClassLoader()
                        .getResource(uri.substring(9)).toString();
            } catch (NullPointerException npe) {
                throw new FileNotFoundException(uri);
            }
        } else if (uri.indexOf(':') < 2) {
            uri = new File(uri).toURI().toString();
        }
        IOD iod = new IOD();
        iod.parse(uri);
        iod.trimToSize();
        return iod;
    }
}