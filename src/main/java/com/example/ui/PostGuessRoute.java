package com.example.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.example.model.GameStatistics;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Session;
import spark.TemplateViewRoute;

import com.example.appl.GameCenter;
import com.example.model.GuessGame;

/**
 * The {@code POST /guess} route handler.
 *
 * @author <a href='mailto:bdbvse@rit.edu'>Bryan Basham</a>
 */
public class PostGuessRoute implements TemplateViewRoute {

  //
  // Constants
  //

  static final String GUESS_PARAM = "myGuess";
  static final String MESSAGE_ATTR = "message";
  static final String HINT_ATTR = "hint";
  static final String MESSAGE_TYPE_ATTR = "messageType";
  static final String YOU_WON_ATTR = "youWon";


  static final String ERROR_TYPE = "error";
  static final String BAD_GUESS = "Nope, try again...";
  static final String VIEW_NAME = "game_form.ftl";

  //
  // Static methods
  //

  /**
   * Make an error message when the guess is not a number.
   */
  static String makeBadArgMessage(final String guessStr) {
    return String.format("You entered '%s' but that's not a number.", guessStr);
  }

  /**
   * Make an error message when the guess is not in the guessing range.
   */
  static String makeInvalidArgMessage(final String guessStr, int upper) {
    return String.format("You entered %s; make a guess between zero and %d.", guessStr, upper);
  }

  //
  // Attributes
  //

  private final GameCenter gameCenter;

  //
  // Constructor
  //

  /**
   * The constructor for the {@code POST /guess} route handler.
   *
   * @param gameCenter
   *    The {@link GameCenter} for the application.
   *
   * @throws NullPointerException
   *    when the {@code gameCenter} parameter is null
   */
  PostGuessRoute(final GameCenter gameCenter) {
    // validation
    Objects.requireNonNull(gameCenter, "gameCenter must not be null");
    //
    this.gameCenter = gameCenter;
  }

  //
  // TemplateViewRoute method
  //

  /**
   * {@inheritDoc}
   */
  @Override
  public ModelAndView handle(Request request, Response response) {
    // start building the View-Model
    final Map<String, Object> vm = new HashMap<>();
    vm.put(GetHomeRoute.TITLE_ATTR, GetGameRoute.TITLE);
    vm.put(GetHomeRoute.NEW_SESSION_ATTR, Boolean.FALSE);

    // retrieve the game object
    final Session session = request.session();
    final GuessGame game = gameCenter.get(session);
    vm.put(GetGameRoute.GAME_BEGINS_ATTR, game.isGameBeginning());
    vm.put(GetGameRoute.GUESSES_LEFT_ATTR, game.guessesLeft());

    // retrieve request parameter
    final String guessStr = request.queryParams(GUESS_PARAM);

    // convert the input
    int guess = -1;
    try {
      guess = Integer.parseInt(guessStr);
    } catch (NumberFormatException e) {
      // re-display the guess form with an error message
      vm.put(GetGameRoute.GAME_UPPER_BOUND,game.getUpperBound());
      return error(vm, makeBadArgMessage(guessStr));
    }

    // validate that the guess is in the range
    if (!game.isValidGuess(guess)) {
      // re-display the guess form with an error message
      vm.put(GetGameRoute.GAME_UPPER_BOUND,game.getUpperBound());
      return error(vm, makeInvalidArgMessage(guessStr, game.getUpperBound()));
    }

    // submit the guess to the game
    final boolean correct = game.makeGuess(guess);

    // Select the next View

    // did you win?
    if (correct) {
        GameStatistics.setLocalWins(GameStatistics.getLocalWins() + 1);
        GameStatistics.setGlobalWins(GameStatistics.getGlobalWins() + 1);
        GameStatistics.setTotalPlays(GameStatistics.getTotalPlays() + 1);
        GameStatistics.setLocalPlays(GameStatistics.getLocalPlays() + 1);
      return youWon(vm, session);
    }
    // no, but you have more guesses?
    else if (game.hasMoreGuesses()) {
      vm.put(GetGameRoute.GUESSES_LEFT_ATTR, game.guessesLeft());
      // TODO: Add the number hint
      String hint;
      if (game.guessIsMore(guess)){
        //TODO: "The number is higher than your guess"
        hint = "HINT: The number is higher than your guess";
      }
      else{
        //TODO: "The numbr is lowr than your guess"
        hint = "HINT: The number is lower than your guess";
      }
      vm.put(GetGameRoute.GAME_UPPER_BOUND,game.getUpperBound());
      return error(vm, BAD_GUESS, hint);
    }
    // otherwise, you lost
    else {
      synchronized (this){
        GameStatistics.setTotalPlays(GameStatistics.getTotalPlays() + 1);
        GameStatistics.setLocalPlays(GameStatistics.getLocalPlays() + 1);
      }
      return youLost(vm, session);
    }
  }

  private ModelAndView error(Map<String, Object> vm, String message) {
    vm.put(MESSAGE_ATTR, message);
    vm.put(MESSAGE_TYPE_ATTR, ERROR_TYPE);
    return new ModelAndView(vm, VIEW_NAME);
  }

  //
  // Private methods
  //

  private ModelAndView error(final Map<String, Object> vm, final String message, final String hint) {
    vm.put(MESSAGE_ATTR, message);
    vm.put(MESSAGE_TYPE_ATTR, ERROR_TYPE);
    vm.put(HINT_ATTR, hint);
    return new ModelAndView(vm, VIEW_NAME);
  }

  private ModelAndView youWon(final Map<String, Object> vm, final Session session) {
    return endGame(true, vm, session);
  }

  private ModelAndView youLost(final Map<String, Object> vm, final Session session) {
    return endGame(false, vm, session);
  }

  private ModelAndView endGame(final boolean youWon, final Map<String, Object> vm, final Session session) {
    gameCenter.end(session);
    // report application-wide game statistics
    vm.put(GetHomeRoute.GAME_STATS_MSG_ATTR, gameCenter.getGameStatsMessage());
    vm.put(GetHomeRoute.GAME_AVG_WINS_MSG_ATTR, gameCenter.getGameAvgMessage());
    vm.put(GetHomeRoute.GAME_STATS_LOCAL_ATTR, gameCenter.getLocalStatsMessage());
    vm.put(YOU_WON_ATTR, youWon);
    return new ModelAndView(vm, GetHomeRoute.VIEW_NAME);
  }
}
