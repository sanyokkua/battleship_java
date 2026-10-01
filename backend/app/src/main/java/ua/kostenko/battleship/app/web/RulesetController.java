package ua.kostenko.battleship.app.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kostenko.battleship.app.web.dto.FleetEntry;
import ua.kostenko.battleship.app.web.dto.Ruleset;
import ua.kostenko.battleship.app.web.dto.RulesetBoard;
import ua.kostenko.battleship.app.web.dto.RulesetList;
import ua.kostenko.battleship.domain.rules.Rulesets;

/** Publishes the two rulesets from the domain constants. */
@RestController
class RulesetController {
    @GetMapping("/api/v1/rulesets")
    RulesetList rulesets() {
        RulesetList list = new RulesetList();
        Rulesets.all().forEach(ruleset -> list.addRulesetsItem(wire(ruleset)));
        return list;
    }

    private static Ruleset wire(ua.kostenko.battleship.domain.rules.Ruleset ruleset) {
        Ruleset wire = new Ruleset()
                .id(ruleset.id())
                .board(new RulesetBoard().rows(ruleset.rows()).columns(ruleset.columns()))
                .shipsMayTouch(ruleset.shipsMayTouch())
                .extraTurnOnHit(ruleset.extraTurnOnHit())
                .revealWaterAroundSunk(ruleset.revealWaterAroundSunk());
        ruleset.fleet()
                .forEach(entry -> wire.addFleetItem(new FleetEntry()
                        .shipTypeId(entry.shipTypeId())
                        .length(entry.length())
                        .count(entry.count())));
        return wire;
    }
}
