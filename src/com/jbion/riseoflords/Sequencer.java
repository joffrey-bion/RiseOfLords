package com.jbion.riseoflords;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.jbion.riseoflords.Sleeper.Speed;
import com.jbion.riseoflords.model.Player;
import com.jbion.riseoflords.network.RoLAdapter;
import com.jbion.riseoflords.util.Log;

public class Sequencer {

    private static final String TAG = Sequencer.class.getSimpleName();

    private final Log log = Log.get();
    private final Sleeper fakeTime = new Sleeper(Speed.REALLY_SLOW);
    private final RoLAdapter rol = new RoLAdapter();

    private static final Comparator<Player> richestFirst = Comparator.comparingInt(Player::getGold).reversed();

    public static void main(String[] args) {
        new Sequencer().start();
    }

    private void start() {
        log.i(TAG, "Starting sequence...");
        login("darklink", "kili");
        AttackParams params = new AttackParams(5, 3);
        PlayerFilter filter = new PlayerFilter(2000, 4000, 50, 400000);
        attackSession(filter, params);
        fakeTime.changePageLong();
        rol.logout();
        log.i(TAG, "End of sequence.");
    }

    /**
     * Logs in with the specified credentials, and wait for standard time.
     * 
     * @param username
     *            the username to connect with
     * @param password
     *            the password to connect with
     */
    private void login(String username, String password) {
        log.d(TAG, "Logging in with username: " + username);
        boolean success = rol.login(username, password);
        log.indent();
        if (success) {
            log.i(TAG, "Logged in with username: " + username);
        } else {
            throw new RuntimeException("Login failure.");
        }
        fakeTime.waitAfterLogin();
        log.deindent(1);
    }

    /**
     * Attacks the richest players matching the filter.
     * 
     * @param filter
     *            the {@link PlayerFilter} to use to choose players
     * @param params
     *            the {@link AttackParams} to use for attacks/actions sequencing
     * @return the total gold stolen
     */
    private int attackSession(PlayerFilter filter, AttackParams params) {
        log.i(TAG, "Starting massive attack on players ranked ", filter.getMinRank(), " to ", filter.getMaxRank(),
                " richer than ", filter.getGoldThreshold(), " gold (", filter.getMaxTurns(), " attacks max)");
        List<Player> players = new ArrayList<>();
        int startRank = filter.getMinRank();
        while (startRank < filter.getMaxRank()) {
            log.d(TAG, "Reading page of players ranked ", startRank, " to ", startRank + 98, "...");
            List<Player> filteredPage = rol.getPlayers(startRank).stream() // stream players
                    .filter(p -> p.getGold() >= filter.getGoldThreshold()) // above gold threshold
                    .filter(p -> p.getRank() <= filter.getMaxRank()) // below max rank
                    .sorted(richestFirst) // richest first
                    .limit(filter.getMaxTurns()) // limit to max turns
                    .collect(Collectors.toList());
            players.addAll(filteredPage);
            fakeTime.readPage();
            startRank += 98;
        }
        log.i(TAG, players.size(), " players matching rank and gold criterias");
        if (players.size() > filter.getMaxTurns()) {
            // too many players, select only the richest
            List<Player> playersToAttack = players.stream() // stream players
                    .sorted(richestFirst) // richest first
                    .limit(filter.getMaxTurns()) // limit to max turns
                    .collect(Collectors.toList());
            return attack(playersToAttack, params);
        } else {
            return attack(players, params);
        }
    }

    /**
     * Attacks all the specified players, following the given parameters.
     * 
     * @param playersToAttack
     *            the filtered list of players to attack. They must verify the thresholds specified
     *            by the given {@link PlayerFilter}.
     * @param params
     *            the parameters to follow. In particular the storing and repair frequencies are
     *            used.
     * @return the total gold stolen
     */
    private int attack(List<Player> playersToAttack, AttackParams params) {
        int totalGoldStolen = 0;
        int nbAttackedPlayers = 0;
        for (Player player : playersToAttack) {
            // attack player
            int goldStolen = attack(player);
            if (goldStolen < 0) {
                // error, player not attacked
                continue;
            }
            totalGoldStolen += goldStolen;
            nbAttackedPlayers++;
            // repair weapons as specified
            if (nbAttackedPlayers % params.getRepairFrequency() == 0) {
                fakeTime.changePage();
                repairWeapons();
                fakeTime.changePage();
            }
            // store gold as specified
            if (nbAttackedPlayers % params.getStoringFrequency() == 0) {
                fakeTime.changePage();
                storeGoldIntoChest();
                fakeTime.pauseWhenSafe();
            }
            fakeTime.changePageLong();
        }
        // store remaining gold
        if (nbAttackedPlayers % params.getStoringFrequency() != 0) {
            fakeTime.changePage();
            storeGoldIntoChest();
            fakeTime.pauseWhenSafe();
        }
        return totalGoldStolen;
    }

    /**
     * Attacks the specified player.
     * 
     * @param player
     *            the player to attack
     * @return the gold stolen from that player
     */
    private int attack(Player player) {
        log.d(TAG, "Attacking player ", player.getName(), "...");
        log.indent();
        log.v(TAG, "Displaying player page...");
        boolean success = rol.displayUserPage(player.getName());
        log.indent();
        if (!success) {
            log.e(TAG, "Something's wrong...");
            return -1;
        }
        log.deindent(1);

        fakeTime.actionInPage();

        log.v(TAG, "Attacking...");
        int goldStolen = rol.attack(player.getName());
        log.indent();
        if (goldStolen > 0) {
            log.v(TAG, "Victory!");
        } else {
            log.v(TAG, "Defeat!");
        }
        log.deindent(2);
        return goldStolen;
    }

    private int storeGoldIntoChest() {
        log.v(TAG, "Storing gold into the chest...");
        log.indent();
        log.v(TAG, "Displaying chest page...");
        int amount = rol.getCurrentGoldFromChestPage();
        log.v(TAG, amount + " gold to store");

        fakeTime.actionInPage();

        log.v(TAG, "Storing everything...");
        log.indent();
        boolean success = rol.storeInChest(amount);
        if (success) {
            log.v(TAG, "The gold is safe!");
        } else {
            log.v(TAG, "Something went wrong!");
        }
        log.deindent(2);
        log.i(TAG, amount, " gold stored in chest");
        return amount;
    }

    private void repairWeapons() {
        log.v(TAG, "Repairing weapons...");
        log.indent();
        log.v(TAG, "Displaying weapons page...");
        int wornness = rol.displayWeaponsPage();
        log.v(TAG, "Weapons worn at ", wornness, "%");

        fakeTime.actionInPage();

        log.v(TAG, "Repair request...");
        boolean success = rol.repairWeapons();
        log.deindent(1);
        if (!success) {
            log.e(TAG, "Couldn't repair weapons, is there enough gold?");
        } else {
            log.i(TAG, "Weapons repaired");
        }
    }
}
