/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.jcr.query.optimize;

import java.util.LinkedList;
import java.util.List;
import org.modeshape.jcr.query.QueryContext;
import org.modeshape.jcr.query.model.Column;
import org.modeshape.jcr.query.model.EquiJoinCondition;
import org.modeshape.jcr.query.model.JoinCondition;
import org.modeshape.jcr.query.model.SelectorName;
import org.modeshape.jcr.query.plan.PlanNode;
import org.modeshape.jcr.query.plan.PlanNode.Property;
import org.modeshape.jcr.query.plan.PlanNode.Type;

/**
 * An {@link OptimizerRule} that adds any missing columns required by the join conditions to the appropriate join source.
 */
public class AddJoinConditionColumnsToSources implements OptimizerRule {

    public static final AddJoinConditionColumnsToSources INSTANCE = new AddJoinConditionColumnsToSources();

    @Override
    public PlanNode execute( QueryContext context,
                             PlanNode plan,
                             LinkedList<OptimizerRule> ruleStack ) {
        final boolean includeSourceName = context.getHints().qualifyExpandedColumnNames;

        // For each of the JOIN nodes ...
        for (PlanNode joinNode : plan.findAllAtOrBelow(Type.JOIN)) {
            JoinCondition condition = joinNode.getProperty(Property.JOIN_CONDITION, JoinCondition.class);
            if (condition instanceof EquiJoinCondition) {
                EquiJoinCondition equiJoinCondition = (EquiJoinCondition)condition;
                SelectorName selector1 = equiJoinCondition.selector1Name();
                Column joinColumn1 = columnFor(equiJoinCondition.selector1Name(),
                                               equiJoinCondition.getProperty1Name(),
                                               includeSourceName);
                Column joinColumn2 = columnFor(equiJoinCondition.selector2Name(),
                                               equiJoinCondition.getProperty2Name(),
                                               includeSourceName);

                // Figure out which side of the join condition goes with which side of the plan nodes ...
                PlanNode left = joinNode.getFirstChild();
                PlanNode right = joinNode.getLastChild();
                if (left.getSelectors().contains(selector1)) {
                    addEquiJoinColumn(context, left, joinColumn1);
                    addEquiJoinColumn(context, right, joinColumn2);
                } else {
                    addEquiJoinColumn(context, left, joinColumn2);
                    addEquiJoinColumn(context, right, joinColumn1);
                }
            }

        }
        return plan;
    }

    /**
     * Make sure that the supplied column is included in the {@link Property#PROJECT_COLUMNS projected columns} on the supplied
     * plan node or its children.
     * 
     * @param context the query context; may not be null
     * @param node the query plan node
     * @param joinColumn the column required by the join
     */
    protected void addEquiJoinColumn( QueryContext context,
                                      PlanNode node,
                                      Column joinColumn ) {
        if (node.getSelectors().contains(joinColumn.selectorName())) {
            // Get the existing projected columns ...
            List<Column> columns = node.getPropertyAsList(Property.PROJECT_COLUMNS, Column.class);
            List<String> types = node.getPropertyAsList(Property.PROJECT_COLUMN_TYPES, String.class);
            if (columns != null && addIfMissing(context, joinColumn, columns, types)) {
                node.setProperty(Property.PROJECT_COLUMNS, columns);
                node.setProperty(Property.PROJECT_COLUMN_TYPES, types);
            }
        }

        // Apply recursively ...
        for (PlanNode child : node) {
            addEquiJoinColumn(context, child, joinColumn);
        }
    }

    /**
     * Check the supplied list of columns for an existing column that matches the supplied {@link Column}, and if none is found
     * add the supplied Column to the list and add an appropriate type.
     * 
     * @param context the query context
     * @param column the column that will be added if not already in the list; may not be null
     * @param columns the list of columns; may not be null
     * @param columnTypes the list of column types; may not be null
     * @return true if the column was added (i.e., the lists were modified), or false if the lists were not modified
     */
    protected boolean addIfMissing( QueryContext context,
                                    Column column,
                                    List<Column> columns,
                                    List<String> columnTypes ) {
        for (Column c : columns) {
            if (!c.selectorName().equals(column.selectorName())) continue;
            String cName = c.getPropertyName();
            if (cName.equals(column.getPropertyName()) || cName.equals(column.getColumnName())) return false;
            cName = c.getColumnName();
            if (cName.equals(column.getPropertyName()) || cName.equals(column.getColumnName())) return false;
        }
        columns.add(column);
        columnTypes.add(context.getTypeSystem().getDefaultType());
        return true;
    }

    protected Column columnFor( SelectorName selector,
                                String property,
                                boolean includeSourceName ) {
        String columnName = includeSourceName ? selector.getString() + "." + property : property;
        return new Column(selector, property, columnName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
