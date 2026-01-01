module Model exposing (GameState, defaultState)

type alias GameState =
    { schemaVersion : Int
    , playerName : String
    , houseName : String
    , ready : Bool
    }


defaultState : GameState
defaultState =
    { schemaVersion = 1
    , playerName = ""
    , houseName = ""
    , ready = False
    }
