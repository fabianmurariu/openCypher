/*
 * Copyright (c) 2015-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.tools.tck

import cucumber.api.DataTable
import org.opencypher.tools.tck.constants.TCKStepDefinitions._

class FeatureFormatChecker extends TCKCucumberTemplate {

  private var lastSeenQuery = ""
  private val orderBy = "(?si).*ORDER BY.*"
  private val stepValidator = new ScenarioFormatValidator

  Background(BACKGROUND) {}

  Given(NAMED_GRAPH) { (name: String) =>
    validateNamedGraph(name).map(msg => throw new InvalidFeatureFormatException(msg))
    stepValidator.reportStep("Given")
  }

  Given(ANY_GRAPH) {
    stepValidator.reportStep("Given")
  }

  Given(EMPTY_GRAPH) {
    stepValidator.reportStep("Given")
  }

  And(INIT_QUERY) { (query: String) => initStep(query) }

  private def initStep(query: String) =
    validateCodeStyle(query).map(msg => throw new InvalidFeatureFormatException(msg))

  And(PARAMETERS) { (table: DataTable) =>
    validateParameters(table).map(msg => throw new InvalidFeatureFormatException(msg))
  }

  When(EXECUTING_QUERY) { (query: String) => whenStep(query)}

  private def whenStep(query: String) = {
    validateCodeStyle(query).map(msg => throw new InvalidFeatureFormatException(msg))
    lastSeenQuery = query
    stepValidator.reportStep("Query")
  }

  Then(EXPECT_RESULT) { (table: DataTable) =>
    validateResults(table).map(msg => throw new InvalidFeatureFormatException(msg))
    // TODO: Some scenarios have `ORDER BY`, but the values are all equal in the ordered column.
    // In this instance, we do not want ordered expectations.
    // We need to find a way to help TCK authors with not forgetting `, in order` for other `ORDER BY` queries, without these false positives.
    // We could do some regex matching and inspecting the values in the ordered column, but it feels complex.

//     if (lastSeenQuery.matches(orderBy))
//       throw new InvalidFeatureFormatException(
//         "Queries with `ORDER BY` needs ordered expectations. Please see the readme.")
    stepValidator.reportStep("Results")
  }

  Then(EXPECT_ERROR) { (status: String, phase: String, detail: String) =>
    validateError(status, phase, detail).map(msg => throw new InvalidFeatureFormatException(msg))
    stepValidator.reportStep("Error")
  }

  Then(EXPECT_SORTED_RESULT) { (table: DataTable) =>
    validateResults(table).map(msg => throw new InvalidFeatureFormatException(msg))
    if (!lastSeenQuery.matches(orderBy))
      throw new InvalidFeatureFormatException(
        "Queries with ordered expectations should have `ORDER BY` in them. Please see the readme.")
    stepValidator.reportStep("Results")
  }

  Then(EXPECT_EMPTY_RESULT) {
    stepValidator.reportStep("Results")
  }

  And(SIDE_EFFECTS) { (table: DataTable) =>
    validateSideEffects(table).map(msg => throw new InvalidFeatureFormatException(msg))
    stepValidator.reportStep("Side-effects")
  }

  And(NO_SIDE_EFFECTS) {
    stepValidator.reportStep("Side-effects")
  }

  When(EXECUTING_CONTROL_QUERY) { (query: String) =>
    validateCodeStyle(query).map(msg => throw new InvalidFeatureFormatException(msg))
    stepValidator.reportStep("Control-query")
  }

  After(_ => stepValidator.checkRequiredSteps())

}

case class InvalidFeatureFormatException(message: String) extends RuntimeException(message)

class ScenarioFormatValidator {
  private var hadGiven = false
  private var numberOfWhenQueries = 0
  private var numberOfThenAssertions = 0
  private var hadError = false
  private var hadSideEffects = false
  private var hadControlQuery = false

  def reportStep(step: String) = step match {
    case "Given" =>
      if (hadGiven) error("Extra `Given` steps specified! Only one is allowed.")
      else hadGiven = true
    case "Query" =>
      numberOfWhenQueries = numberOfWhenQueries + 1
    case "Results" =>
      if (numberOfThenAssertions > numberOfWhenQueries && !hadControlQuery) error("Extra `Then expect results` steps specified! Only one is allowed.")
      else if (hadError) error("Both results and error expectations found; they are mutually exclusive.")
      numberOfThenAssertions = numberOfThenAssertions + 1
    case "Error" =>
      if (hadError) error("Extra `Then expect error` steps specified! Only one is allowed.")
      else if (numberOfThenAssertions > 0) error("Both results and error expectations found; they are mutually exclusive.")
      else hadError = true
    case "Side-effects" =>
      if (hadSideEffects) error("Extra `And side effects` steps specified! Only one is allowed.")
      else hadSideEffects = true
    case "Control-query" => hadControlQuery = true

    case _ => throw new IllegalArgumentException("Unknown step identifier. Valid identifiers are Given, Query, Results, Error, Side-effects.")
  }

  def checkRequiredSteps() = {
    val correctWhenThenSetup = numberOfWhenQueries == (if (hadControlQuery) numberOfThenAssertions - 1 else numberOfThenAssertions)
    if (hadGiven && numberOfWhenQueries > 0 && (correctWhenThenSetup && hadSideEffects || hadError)) {
      reset()
    } else
      error(s"The scenario setup was incomplete: Given: $hadGiven, Query: $numberOfWhenQueries, Results or error: ${numberOfThenAssertions > 0 || hadError}, Side effects: $hadSideEffects")
  }

  private def reset() = {
    hadGiven = false
    numberOfWhenQueries = 0
    numberOfThenAssertions = 0
    hadError = false
    hadSideEffects = false
    hadControlQuery = false
  }

  private def error(msg: String) = {
    reset()
    throw new InvalidFeatureFormatException(msg)
  }
}
