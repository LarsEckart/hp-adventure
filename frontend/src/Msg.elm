module Msg exposing (Msg(..))

import Api
import Time

type Msg
    = UpdateName String
    | UpdateHouse String
    | UpdateActionInput String
    | OnlineStatusChanged Bool
    | StartAdventure
    | GotStartTime Time.Posix
    | SendAction
    | UseSuggestedAction String
    | GotStoryResponse (Result Api.HttpError Api.StoryResponse)
    | ToggleInventory
    | ToggleHistory
    | RequestAbandon
    | ConfirmAbandon
    | CancelAbandon
    | DismissNotice
    | ResetState
